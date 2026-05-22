package ai.claudecode.esgt2.vw.api;

import java.util.List;
import java.util.UUID;

/**
 * 검증 워크스페이스 서비스 공개 인터페이스 (vw 모듈 api 패키지).
 * 다른 모듈은 이 인터페이스만을 통해 vw 기능에 접근한다.
 */
public interface SnapshotService {

    /**
     * APPROVED 보고서에서 SHA-256 불변 스냅샷 생성 (T-8-07).
     * 미승인 보고서 → {@code EsgException(REPORT_NOT_APPROVED)}.
     */
    SnapshotResponse createSnapshot(UUID tenantId, UUID actorId, UUID reportId);

    /**
     * 스냅샷 조회. 미존재 → {@code EsgException(SNAPSHOT_NOT_FOUND)}.
     * VERIFIER는 자신에게 지정된 스냅샷만 조회 가능.
     */
    SnapshotResponse getSnapshot(UUID tenantId, UUID snapshotId);

    /** 코멘트 작성 (T-8-09). {@code @Auditable} 적용. */
    CommentResponse addComment(UUID tenantId, UUID actorId, UUID snapshotId, String body);

    /** 코멘트 목록 조회 (생성 시각 오름차순). */
    List<CommentResponse> listComments(UUID tenantId, UUID snapshotId);

    /** 검증 완료 서명 (T-8-10). {@code @Auditable} 적용. 스냅샷당 1회만 허용. */
    void signSnapshot(UUID tenantId, UUID actorId, UUID snapshotId, String note);

    /** 서명 여부 확인. */
    boolean isSigned(UUID tenantId, UUID snapshotId);
}
