package ai.claudecode.esgt2.entity.domain;

import ai.claudecode.esgt2.entity.api.ConsolidationMethod;
import ai.claudecode.esgt2.entity.api.EntityRelationship;
import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T-1-20: 검증 우선 원칙 — ERROR severity 시 create() 실행 전 차단
 * esg-t1 BUG-P3-04 교훈: 서비스에서 검증 전에 도메인 생성하면 불완전한 객체가 저장됨
 */
class ValidationFirstPrincipleTest {

    @Test
    void 잘못된_입력으로_create_호출_시_EsgException이_발생하고_객체가_생성되지_않는다() {
        var cmd = new CreateLegalEntityCommand(UUID.randomUUID(), "", "KR", LegalEntityType.PARENT);

        // create()를 호출하기 전에 예외가 발생해야 함 (도메인 팩토리가 검증 역할을 겸함)
        LegalEntity[] capturedResult = {null};

        assertThatThrownBy(() -> {
            capturedResult[0] = LegalEntity.create(cmd);
        })
            .isInstanceOf(EsgException.class)
            .satisfies(e -> assertThat(((EsgException) e).getErrorCode())
                .isEqualTo(EsgErrorCode.VALIDATION_FAILED));

        // 예외 발생 후 반환된 객체가 없음
        assertThat(capturedResult[0]).isNull();
    }

    @Test
    void 유효한_입력이면_create가_성공하고_완전한_객체를_반환한다() {
        var cmd = new CreateLegalEntityCommand(UUID.randomUUID(), "삼성전자", "KR", LegalEntityType.PARENT);

        LegalEntity entity = LegalEntity.create(cmd);

        // 모든 필드가 정상 설정됨
        assertThat(entity.id()).isNotNull();
        assertThat(entity.name()).isEqualTo("삼성전자");
        assertThat(entity.isActive()).isTrue();
    }

    @Test
    void EntityRelationship_잘못된_지분율로_create_시_차단된다() {
        var cmd = new CreateEntityRelationshipCommand(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            null, ConsolidationMethod.EQUITY,
            java.time.LocalDate.now(), null);

        EntityRelationship[] captured = {null};

        assertThatThrownBy(() -> {
            captured[0] = EntityRelationship.create(cmd);
        })
            .isInstanceOf(EsgException.class)
            .satisfies(e -> assertThat(((EsgException) e).getErrorCode())
                .isEqualTo(EsgErrorCode.VALIDATION_FAILED));

        assertThat(captured[0]).isNull();
    }
}
