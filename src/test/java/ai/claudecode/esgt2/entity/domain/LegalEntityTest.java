package ai.claudecode.esgt2.entity.domain;

import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegalEntityTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Test
    void 유효한_입력으로_법인이_생성된다() {
        var cmd = new CreateLegalEntityCommand(TENANT_ID, "삼성전자", "KR", LegalEntityType.PARENT);
        LegalEntity entity = LegalEntity.create(cmd);

        assertThat(entity.tenantId()).isEqualTo(TENANT_ID);
        assertThat(entity.name()).isEqualTo("삼성전자");
        assertThat(entity.countryCode()).isEqualTo("KR");
        assertThat(entity.entityType()).isEqualTo(LegalEntityType.PARENT);
        assertThat(entity.isActive()).isTrue();
    }

    @Test
    void 법인명이_null이면_예외가_발생한다() {
        var cmd = new CreateLegalEntityCommand(TENANT_ID, null, "KR", LegalEntityType.PARENT);

        assertThatThrownBy(() -> LegalEntity.create(cmd))
            .isInstanceOf(EsgException.class)
            .satisfies(e -> assertThat(((EsgException) e).getErrorCode())
                .isEqualTo(EsgErrorCode.VALIDATION_FAILED));
    }

    @Test
    void 법인명이_공백이면_예외가_발생한다() {
        var cmd = new CreateLegalEntityCommand(TENANT_ID, "   ", "KR", LegalEntityType.PARENT);

        assertThatThrownBy(() -> LegalEntity.create(cmd))
            .isInstanceOf(EsgException.class)
            .satisfies(e -> assertThat(((EsgException) e).getErrorCode())
                .isEqualTo(EsgErrorCode.VALIDATION_FAILED));
    }

    @Test
    void 국가코드가_null이면_예외가_발생한다() {
        var cmd = new CreateLegalEntityCommand(TENANT_ID, "삼성전자", null, LegalEntityType.PARENT);

        assertThatThrownBy(() -> LegalEntity.create(cmd))
            .isInstanceOf(EsgException.class)
            .satisfies(e -> assertThat(((EsgException) e).getErrorCode())
                .isEqualTo(EsgErrorCode.VALIDATION_FAILED));
    }

    @Test
    void 국가코드가_2자리가_아니면_예외가_발생한다() {
        var cmd = new CreateLegalEntityCommand(TENANT_ID, "삼성전자", "KOR", LegalEntityType.PARENT);

        assertThatThrownBy(() -> LegalEntity.create(cmd))
            .isInstanceOf(EsgException.class)
            .satisfies(e -> assertThat(((EsgException) e).getErrorCode())
                .isEqualTo(EsgErrorCode.VALIDATION_FAILED));
    }

    @Test
    void 국가코드가_소문자면_예외가_발생한다() {
        var cmd = new CreateLegalEntityCommand(TENANT_ID, "삼성전자", "kr", LegalEntityType.PARENT);

        assertThatThrownBy(() -> LegalEntity.create(cmd))
            .isInstanceOf(EsgException.class)
            .satisfies(e -> assertThat(((EsgException) e).getErrorCode())
                .isEqualTo(EsgErrorCode.VALIDATION_FAILED));
    }

    @Test
    void entityType이_null이면_예외가_발생한다() {
        var cmd = new CreateLegalEntityCommand(TENANT_ID, "삼성전자", "KR", null);

        assertThatThrownBy(() -> LegalEntity.create(cmd))
            .isInstanceOf(EsgException.class)
            .satisfies(e -> assertThat(((EsgException) e).getErrorCode())
                .isEqualTo(EsgErrorCode.VALIDATION_FAILED));
    }

    @Test
    void tenantId가_null이면_예외가_발생한다() {
        var cmd = new CreateLegalEntityCommand(null, "삼성전자", "KR", LegalEntityType.PARENT);

        assertThatThrownBy(() -> LegalEntity.create(cmd))
            .isInstanceOf(EsgException.class)
            .satisfies(e -> assertThat(((EsgException) e).getErrorCode())
                .isEqualTo(EsgErrorCode.VALIDATION_FAILED));
    }
}
