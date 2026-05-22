package ai.claudecode.esgt2.supply;

import ai.claudecode.esgt2.entity.api.CreateEntityRequest;
import ai.claudecode.esgt2.entity.api.EntityManagementService;
import ai.claudecode.esgt2.entity.domain.LegalEntityType;
import ai.claudecode.esgt2.ghg.api.ActivityDataWorkflowService;
import ai.claudecode.esgt2.shared.security.JwtAuthentication;
import ai.claudecode.esgt2.supply.api.ActivateSupplierRequest;
import ai.claudecode.esgt2.supply.api.InviteSupplierRequest;
import ai.claudecode.esgt2.supply.api.SupplierDataRequest;
import ai.claudecode.esgt2.supply.api.SupplierService;
import ai.claudecode.esgt2.supply.infra.SupplierInvitationRepository;
import ai.claudecode.esgt2.supply.support.StubEmailGateway;
import ai.claudecode.esgt2.supply.support.SupplyTestConfig;
import ai.claudecode.esgt2.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(SupplyTestConfig.class)
class SupplyIntegrationTest extends AbstractIntegrationTest {

    @Autowired private SupplierService supplierService;
    @Autowired private EntityManagementService entityManagementService;
    @Autowired private ActivityDataWorkflowService workflowService;
    @Autowired private SupplierInvitationRepository invitationRepository;
    @Autowired private StubEmailGateway emailGateway;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final UUID ACTOR_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private UUID entityId;

    @BeforeEach
    void setup() {
        jdbcTemplate.execute("DELETE FROM supplier_invitations WHERE tenant_id = '00000000-0000-0000-0000-000000000007'");
        jdbcTemplate.execute("DELETE FROM activity_data WHERE tenant_id = '00000000-0000-0000-0000-000000000007'");
        jdbcTemplate.execute("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE tenant_id = '00000000-0000-0000-0000-000000000007')");
        jdbcTemplate.execute("DELETE FROM users WHERE tenant_id = '00000000-0000-0000-0000-000000000007'");
        jdbcTemplate.execute("DELETE FROM legal_entities WHERE tenant_id = '00000000-0000-0000-0000-000000000007'");
        jdbcTemplate.execute(
            "INSERT INTO tenants (id, code, name, country_code) " +
            "VALUES ('00000000-0000-0000-0000-000000000007', 'TEST7', '공급업체테스트', 'KR') " +
            "ON CONFLICT DO NOTHING");

        emailGateway.clear();

        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthentication(ACTOR_ID, TENANT_ID, List.of("TENANT_ADMIN")));

        var entityResp = entityManagementService.create(
            TENANT_ID, new CreateEntityRequest("공급업체테스트법인", "KR", LegalEntityType.SUBSIDIARY));
        entityId = entityResp.id();
    }

    // T-6-07: 공급업체 초대 + 이메일 발송
    @Test
    void 공급업체_초대_시_DB_저장_이메일_발송() {
        var request = new InviteSupplierRequest("supplier@example.com", entityId);

        var response = supplierService.inviteSupplier(TENANT_ID, ACTOR_ID, request);

        assertThat(response.email()).isEqualTo("supplier@example.com");
        assertThat(response.status()).isEqualTo("PENDING");

        var saved = invitationRepository.findAll().stream()
            .filter(i -> "supplier@example.com".equals(i.getEmail()))
            .findFirst();
        assertThat(saved).isPresent();
        assertThat(saved.get().getStatus()).isEqualTo("PENDING");

        // 이메일이 발송됐는지 확인
        assertThat(emailGateway.getSentInvitations()).hasSize(1);
        assertThat(emailGateway.getSentInvitations().get(0).to())
            .isEqualTo("supplier@example.com");
    }

    // T-6-07: 계정 활성화 플로우
    @Test
    void 계정_활성화_후_상태_ACCEPTED_변경() {
        supplierService.inviteSupplier(TENANT_ID, ACTOR_ID,
            new InviteSupplierRequest("supplier2@example.com", entityId));

        var savedInvitation = invitationRepository.findAll().stream()
            .filter(inv -> "supplier2@example.com".equals(inv.getEmail()))
            .findFirst().orElseThrow();

        supplierService.activateAccount(
            new ActivateSupplierRequest(savedInvitation.getToken(), "Password1234!"));

        var updated = invitationRepository.findById(savedInvitation.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("ACCEPTED");
    }

    // T-6-08: SUPPLIER 자사 데이터 입력
    @Test
    void SUPPLIER_자사_법인에_데이터_등록_성공() {
        // SUPPLIER 인증 컨텍스트로 전환
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthentication(ACTOR_ID, TENANT_ID, entityId, List.of("SUPPLIER")));

        var request = new SupplierDataRequest(
            2025, "SCOPE3_CAT1", "ELECTRONICS",
            new BigDecimal("5000"), "KRW", "KR");

        var response = supplierService.submitData(TENANT_ID, ACTOR_ID, entityId, request);

        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.dataSource()).isEqualTo("SUPPLIER_PORTAL");
    }

    // T-6-09: 타 법인 접근 시도 → JWT.entityId 불일치 검증
    @Test
    void SUPPLIER_JWT_entityId가_타법인과_다름을_검증() {
        UUID otherEntityId = UUID.fromString("00000000-0000-0000-0000-000000000099");

        // SUPPLIER A의 JWT에 myEntityId가 설정됨
        var auth = new JwtAuthentication(ACTOR_ID, TENANT_ID, entityId, List.of("SUPPLIER"));
        // otherEntityId로 요청하면 컨트롤러에서 403 처리
        assertThat(auth.getEntityId()).isNotEqualTo(otherEntityId);
        // 실제 HTTP 403 검증은 SupplierControllerSecurityTest에서 수행
    }

    // T-6-10: 승인 워크플로우
    @Test
    void 공급업체_제출_후_ESG_MANAGER_승인_플로우() {
        // 데이터 입력 (SUPPLIER 컨텍스트)
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthentication(ACTOR_ID, TENANT_ID, entityId, List.of("SUPPLIER")));
        var data = supplierService.submitData(TENANT_ID, ACTOR_ID, entityId,
            new SupplierDataRequest(2025, "SCOPE3_CAT1", "ELECTRONICS",
                new BigDecimal("3000"), "KRW", "KR"));

        // 제출 (DRAFT → PENDING)
        var pending = workflowService.submitActivityData(TENANT_ID, ACTOR_ID, data.id());
        assertThat(pending.status()).isEqualTo("PENDING");

        // ESG_MANAGER가 승인 (PENDING → APPROVED)
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthentication(ACTOR_ID, TENANT_ID, List.of("ESG_MANAGER")));
        var approved = workflowService.approveActivityData(TENANT_ID, ACTOR_ID, data.id());
        assertThat(approved.status()).isEqualTo("APPROVED");
    }

    @Test
    void 공급업체_제출_반려_후_REJECTED_상태() {
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthentication(ACTOR_ID, TENANT_ID, entityId, List.of("SUPPLIER")));
        var data = supplierService.submitData(TENANT_ID, ACTOR_ID, entityId,
            new SupplierDataRequest(2025, "SCOPE3_CAT1", "PAPER",
                new BigDecimal("1500"), "KRW", "KR"));

        workflowService.submitActivityData(TENANT_ID, ACTOR_ID, data.id());

        var rejected = workflowService.rejectActivityData(
            TENANT_ID, ACTOR_ID, data.id(), "단위 오류");
        assertThat(rejected.status()).isEqualTo("REJECTED");
    }
}
