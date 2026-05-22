package ai.claudecode.esgt2.audit;

import ai.claudecode.esgt2.audit.infra.AuditLogRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * AuditLogRepository가 append-only임을 컴파일 타임에 검증 (T-7-13).
 * Repository<T,ID> 마커 인터페이스 상속 → delete* 메서드 컴파일 타임 미노출.
 * 이 테스트는 코드베이스에서 delete 메서드가 없음을 반영한 문서 역할을 한다.
 */
class AuditLogRepositoryAppendOnlyTest {

    @Test
    void AuditLogRepository는_append_only_인터페이스이다() {
        // AuditLogRepository extends Repository<T,ID> (not JpaRepository)
        // 아래 메서드들이 존재하지 않음을 반영적으로 확인
        var methods = java.util.Arrays.stream(AuditLogRepository.class.getMethods())
            .map(m -> m.getName())
            .toList();

        assertThat(methods).doesNotContain("delete", "deleteById", "deleteAll",
            "deleteAllById", "deleteAllInBatch");
    }
}
