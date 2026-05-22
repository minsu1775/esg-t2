package ai.claudecode.esgt2.vw.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "검증 코멘트 응답")
public record CommentResponse(
    @Schema(description = "코멘트 ID") UUID id,
    @Schema(description = "스냅샷 ID") UUID snapshotId,
    @Schema(description = "작성자 ID") UUID authorId,
    @Schema(description = "내용") String body,
    @Schema(description = "작성 시각") Instant createdAt
) {}
