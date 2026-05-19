package ai.claudecode.esgt2.entity.api;

import ai.claudecode.esgt2.entity.domain.LegalEntityType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "법인 정보")
public record EntityResponse(
    @Schema(description = "법인 ID") UUID id,
    @Schema(description = "법인명") String name,
    @Schema(description = "국가코드 (ISO 3166-1 alpha-2)") String countryCode,
    @Schema(description = "법인 유형") LegalEntityType entityType,
    @Schema(description = "활성 여부") boolean isActive
) {}
