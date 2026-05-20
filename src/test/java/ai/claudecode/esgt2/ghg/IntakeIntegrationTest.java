package ai.claudecode.esgt2.ghg;

import ai.claudecode.esgt2.audit.infra.OutboxEventRepository;
import ai.claudecode.esgt2.entity.api.CreateEntityRequest;
import ai.claudecode.esgt2.entity.api.EntityManagementService;
import ai.claudecode.esgt2.entity.domain.LegalEntityType;
import ai.claudecode.esgt2.ghg.api.CsvUploadResponse;
import ai.claudecode.esgt2.ghg.api.IntakeService;
import ai.claudecode.esgt2.ghg.infra.ActivityDataRepository;
import ai.claudecode.esgt2.shared.security.JwtAuthentication;
import ai.claudecode.esgt2.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IntakeIntegrationTest extends AbstractIntegrationTest {

    @Autowired private IntakeService intakeService;
    @Autowired private EntityManagementService entityManagementService;
    @Autowired private ActivityDataRepository activityDataRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    // 4xx/5xx 응답에서 예외 대신 ResponseEntity를 반환하도록 설정
    private final RestTemplate restTemplate = new RestTemplate() {{
        setErrorHandler(new DefaultResponseErrorHandler() {
            @Override public boolean hasError(org.springframework.http.client.ClientHttpResponse r) { return false; }
        });
    }};

    @LocalServerPort
    private int port;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000006");
    private static final UUID ACTOR_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String WEBHOOK_SECRET = "dev-webhook-secret-must-change-in-prod";

    private UUID entityId;

    @BeforeEach
    void setup() {
        jdbcTemplate.execute("DELETE FROM emission_records WHERE tenant_id = '00000000-0000-0000-0000-000000000006'");
        jdbcTemplate.execute("DELETE FROM activity_data WHERE tenant_id = '00000000-0000-0000-0000-000000000006'");
        jdbcTemplate.execute("DELETE FROM legal_entities WHERE tenant_id = '00000000-0000-0000-0000-000000000006'");
        jdbcTemplate.execute("DELETE FROM audit_logs");
        outboxEventRepository.deleteAll();
        jdbcTemplate.execute(
            "INSERT INTO tenants (id, code, name, country_code) " +
            "VALUES ('00000000-0000-0000-0000-000000000006', 'TEST6', 'CSV테스트테넌트', 'KR') " +
            "ON CONFLICT DO NOTHING");

        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthentication(ACTOR_ID, TENANT_ID, List.of("ESG_MANAGER")));

        var entityResp = entityManagementService.create(
            TENANT_ID, new CreateEntityRequest("CSV테스트법인", "KR", LegalEntityType.PARENT));
        entityId = entityResp.id();
    }

    // T-6-01: 100행 CSV 업로드 멱등성
    @Test
    void CSV_100행_업로드_모두_성공() {
        var csv = buildCsv(100);

        CsvUploadResponse result = intakeService.uploadCsv(TENANT_ID, entityId, csv);

        assertThat(result.totalRows()).isEqualTo(100);
        assertThat(result.successCount()).isEqualTo(100);
        assertThat(result.errorCount()).isEqualTo(0);
        assertThat(activityDataRepository
            .findByTenantIdAndEntityIdAndReportingYear(TENANT_ID, entityId, 2025))
            .hasSize(100);
    }

    // T-6-01: 동일 CSV 재업로드 → 멱등성 (100건 SKIPPED)
    @Test
    void 동일_CSV_재업로드_100행_모두_건너뜀() {
        intakeService.uploadCsv(TENANT_ID, entityId, buildCsv(100));

        CsvUploadResponse result = intakeService.uploadCsv(TENANT_ID, entityId, buildCsv(100));

        assertThat(result.skipCount()).isEqualTo(100);
        assertThat(result.successCount()).isEqualTo(0);
        assertThat(activityDataRepository
            .findByTenantIdAndEntityIdAndReportingYear(TENANT_ID, entityId, 2025))
            .hasSize(100);
    }

    // T-6-12: 중간 행 오류 시 이전 행 보존 (REQUIRES_NEW)
    @Test
    void 중간_행_오류_시_이전_성공_행_보존() {
        String csvContent =
            "reporting_year,category,sub_category,quantity,unit,country_code,data_source,data_quality,lifetime_years\n" +
            "2025,SCOPE3_CAT1,ITEM_A,10000,KRW,KR,MANUAL,,\n" +
            "2025,SCOPE3_CAT2,,500000,KRW,KR,,,\n" +
            ",SCOPE3_CAT1,,10000,KRW,KR,,,\n" +
            "2025,SCOPE3_CAT1,ITEM_B,8000,KRW,KR,MANUAL,,\n";
        var csv = new ByteArrayResource(csvContent.getBytes(StandardCharsets.UTF_8));

        CsvUploadResponse result = intakeService.uploadCsv(TENANT_ID, entityId, csv);

        assertThat(result.successCount()).isEqualTo(3);
        assertThat(result.errorCount()).isEqualTo(1);
        assertThat(result.nonSuccessRows().get(0).lineNumber()).isEqualTo(4);
        // REQUIRES_NEW 덕분에 오류 행이 롤백돼도 나머지 3건은 커밋됨
        assertThat(activityDataRepository
            .findByTenantIdAndEntityIdAndReportingYear(TENANT_ID, entityId, 2025))
            .hasSize(3);
    }

    // T-6-13: 중복 항목 재업로드 → WARN 로그 + 계속 처리 (ERROR 없음)
    @Test
    void 부분_중복_CSV_재업로드_기존행_SKIPPED_신규행_SUCCESS() {
        String firstCsv =
            "reporting_year,category,sub_category,quantity,unit,country_code,data_source,data_quality,lifetime_years\n" +
            "2025,SCOPE3_CAT1,ELECTRONICS,10000,KRW,KR,MANUAL,,\n";
        intakeService.uploadCsv(TENANT_ID, entityId,
            new ByteArrayResource(firstCsv.getBytes(StandardCharsets.UTF_8)));

        String mixedCsv =
            "reporting_year,category,sub_category,quantity,unit,country_code,data_source,data_quality,lifetime_years\n" +
            "2025,SCOPE3_CAT1,ELECTRONICS,10000,KRW,KR,MANUAL,,\n" +
            "2025,SCOPE3_CAT2,,500000,KRW,KR,,,\n";
        CsvUploadResponse result = intakeService.uploadCsv(TENANT_ID, entityId,
            new ByteArrayResource(mixedCsv.getBytes(StandardCharsets.UTF_8)));

        assertThat(result.skipCount()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.errorCount()).isEqualTo(0);
        assertThat(activityDataRepository
            .findByTenantIdAndEntityIdAndReportingYear(TENANT_ID, entityId, 2025))
            .hasSize(2);
    }

    // T-6-05: Webhook 서명 불일치 → 401
    @Test
    void Webhook_서명_불일치_시_401() {
        String body = "[{\"entityId\":\"" + entityId + "\",\"reportingYear\":2025," +
            "\"category\":\"SCOPE3_CAT1\",\"subCategory\":\"ELECTRONICS\"," +
            "\"quantity\":10000,\"unit\":\"KRW\",\"countryCode\":\"KR\"}]";

        var headers = new HttpHeaders();
        headers.set("X-Hub-Signature-256", "invalid-signature");
        headers.setContentType(MediaType.APPLICATION_JSON);

        var response = restTemplate.exchange(
            "http://localhost:" + port + "/api/v1/intake/tenants/" + TENANT_ID + "/webhook",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    // T-6-04: Webhook 유효 서명 → 데이터 저장
    @Test
    void Webhook_유효_서명_ActivityData_저장() throws Exception {
        String body = "[{\"entityId\":\"" + entityId + "\",\"reportingYear\":2025," +
            "\"category\":\"SCOPE3_CAT1\",\"subCategory\":\"WEBHOOK_TEST\"," +
            "\"quantity\":99999,\"unit\":\"KRW\",\"countryCode\":\"KR\",\"dataSource\":\"SAP_ERP\"}]";

        var headers = new HttpHeaders();
        headers.set("X-Hub-Signature-256", computeHmac(body, WEBHOOK_SECRET));
        headers.setContentType(MediaType.APPLICATION_JSON);

        var response = restTemplate.exchange(
            "http://localhost:" + port + "/api/v1/intake/tenants/" + TENANT_ID + "/webhook",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            CsvUploadResponse.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().successCount()).isEqualTo(1);
        assertThat(activityDataRepository
            .findByTenantIdAndEntityIdAndReportingYear(TENANT_ID, entityId, 2025))
            .anyMatch(ad -> "SAP_ERP".equals(ad.getDataSource()));
    }

    // T-6R-01: 음수 quantity → ERROR 행 반환
    @Test
    void 음수_quantity_행은_ERROR_반환() {
        String csvContent =
            "reporting_year,category,sub_category,quantity,unit,country_code,data_source,data_quality,lifetime_years\n" +
            "2025,SCOPE3_CAT1,ITEM_OK,10000,KRW,KR,MANUAL,,\n" +
            "2025,SCOPE3_CAT1,ITEM_NEG,-500,KRW,KR,MANUAL,,\n";
        var csv = new ByteArrayResource(csvContent.getBytes(StandardCharsets.UTF_8));

        CsvUploadResponse result = intakeService.uploadCsv(TENANT_ID, entityId, csv);

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.errorCount()).isEqualTo(1);
        assertThat(result.nonSuccessRows().get(0).message()).contains("quantity는 양수여야 합니다");
    }

    // T-6R-02: 존재하지 않는 entityId → 예외 발생
    @Test
    void 존재하지_않는_entityId로_CSV_업로드_시_예외() {
        var unknownEntityId = UUID.fromString("00000000-0000-0000-0000-000000009999");
        var csv = new ByteArrayResource(
            ("reporting_year,category,sub_category,quantity,unit,country_code,data_source,data_quality,lifetime_years\n" +
             "2025,SCOPE3_CAT1,ITEM_A,10000,KRW,KR,MANUAL,,\n")
                .getBytes(StandardCharsets.UTF_8));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> intakeService.uploadCsv(TENANT_ID, unknownEntityId, csv))
            .isInstanceOf(ai.claudecode.esgt2.shared.exception.EsgException.class);
    }

    private ByteArrayResource buildCsv(int rowCount) {
        var sb = new StringBuilder(
            "reporting_year,category,sub_category,quantity,unit,country_code,data_source,data_quality,lifetime_years\n");
        for (int i = 0; i < rowCount; i++) {
            sb.append(String.format("2025,SCOPE3_CAT1,ITEM_%d,10000,KRW,KR,MANUAL,,\n", i));
        }
        return new ByteArrayResource(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String computeHmac(String payload, String secret) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] computed = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(computed);
    }
}
