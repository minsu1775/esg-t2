package ai.claudecode.esgt2.ghg.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActivityDataCorrectionDomainTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ENTITY_ID = UUID.randomUUID();

    private ActivityData makeOriginal() {
        return ActivityData.create(new CreateActivityDataCommand(
            TENANT_ID, ENTITY_ID, 2025, "SCOPE1_FUEL", "GAS",
            new BigDecimal("1000"), "GJ", "KR", "MANUAL", null, null));
    }

    // T-6B-01: 정정 → 새 UUID, 원본 ID 참조, 원본 quantity 불변
    @Test
    void 정정_시_새_UUID_생성_correctionOf_원본_참조() {
        var original = makeOriginal();
        var cmd = new CorrectActivityDataCommand(
            TENANT_ID, ENTITY_ID, 2025, "SCOPE1_FUEL", "GAS",
            new BigDecimal("1200"), "GJ", "KR", "MANUAL", null, null,
            "계량기 재측정 결과 반영");

        var corrected = ActivityData.correct(original, cmd);

        assertThat(corrected.id()).isNotEqualTo(original.id());
        assertThat(corrected.correctionOf()).isEqualTo(original.id());
        assertThat(corrected.correctionReason()).isEqualTo("계량기 재측정 결과 반영");
        assertThat(corrected.quantity()).isEqualByComparingTo(new BigDecimal("1200"));
        // 원본 quantity 불변 (record는 불변)
        assertThat(original.quantity()).isEqualByComparingTo(new BigDecimal("1000"));
    }

    // T-6B-02: 정정 사유 빈 문자열 → 예외
    @Test
    void 정정_사유_빈_문자열_시_예외() {
        assertThatThrownBy(() -> new CorrectActivityDataCommand(
            TENANT_ID, ENTITY_ID, 2025, "SCOPE1_FUEL", "GAS",
            new BigDecimal("1200"), "GJ", "KR", "MANUAL", null, null, ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("정정 사유");
    }

    // T-6B-02: 정정 사유 null → 예외
    @Test
    void 정정_사유_null_시_예외() {
        assertThatThrownBy(() -> new CorrectActivityDataCommand(
            TENANT_ID, ENTITY_ID, 2025, "SCOPE1_FUEL", "GAS",
            new BigDecimal("1200"), "GJ", "KR", "MANUAL", null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("정정 사유");
    }
}
