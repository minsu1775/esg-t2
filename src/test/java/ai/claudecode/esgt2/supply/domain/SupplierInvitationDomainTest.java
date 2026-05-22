package ai.claudecode.esgt2.supply.domain;

import ai.claudecode.esgt2.shared.exception.EsgException;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupplierInvitationDomainTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ENTITY_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID  = UUID.randomUUID();

    @Test
    void 초대_생성_시_PENDING_상태_토큰_포함() {
        var inv = SupplierInvitation.create(TENANT_ID, ENTITY_ID, "supplier@test.com", ACTOR_ID);

        assertThat(inv.status()).isEqualTo("PENDING");
        assertThat(inv.token()).isNotNull();
        assertThat(inv.email()).isEqualTo("supplier@test.com");
        assertThat(inv.expiresAt()).isAfter(OffsetDateTime.now());
    }

    @Test
    void 이메일_공백_시_예외() {
        assertThatThrownBy(() ->
            SupplierInvitation.create(TENANT_ID, ENTITY_ID, "  ", ACTOR_ID))
            .isInstanceOf(EsgException.class);
    }

    @Test
    void 만료된_초대_활성화_시도_시_예외() {
        var expired = new SupplierInvitation(
            UUID.randomUUID(), TENANT_ID, ENTITY_ID, "s@t.com",
            UUID.randomUUID(), "PENDING", ACTOR_ID,
            OffsetDateTime.now().minusDays(1),  // 만료
            null, OffsetDateTime.now().minusDays(8));

        assertThatThrownBy(expired::validateForActivation)
            .isInstanceOf(EsgException.class)
            .hasMessageContaining("만료");
    }

    @Test
    void ACCEPTED_상태_초대_재활성화_시도_시_예외() {
        var accepted = new SupplierInvitation(
            UUID.randomUUID(), TENANT_ID, ENTITY_ID, "s@t.com",
            UUID.randomUUID(), "ACCEPTED", ACTOR_ID,
            OffsetDateTime.now().plusDays(7),
            OffsetDateTime.now(), OffsetDateTime.now().minusDays(1));

        assertThatThrownBy(accepted::validateForActivation)
            .isInstanceOf(EsgException.class)
            .hasMessageContaining("이미 사용");
    }
}
