package ai.claudecode.esgt2.ghg.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "산식 버전 응답")
public record FormulaVersionResponse(
    @Schema(description = "ID") UUID id,
    @Schema(description = "산식 코드") String code,
    @Schema(description = "버전") String version,
    @Schema(description = "수식") String expression,
    @Schema(description = "적용 GHG 카테고리") String ghgCategory,
    @Schema(description = "상태 (ACTIVE/INACTIVE)") String status,
    @Schema(description = "등록 시각") Instant createdAt
) {}
