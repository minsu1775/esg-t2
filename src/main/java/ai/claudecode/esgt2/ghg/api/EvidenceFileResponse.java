package ai.claudecode.esgt2.ghg.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "증빙 파일 응답")
public record EvidenceFileResponse(
    @Schema(description = "증빙 파일 ID") UUID id,
    @Schema(description = "원본 파일명") String originalFilename,
    @Schema(description = "파일 크기 (bytes)") Long fileSizeBytes,
    @Schema(description = "SHA-256 해시") String sha256Hash,
    @Schema(description = "업로드 시각") Instant createdAt
) {}
