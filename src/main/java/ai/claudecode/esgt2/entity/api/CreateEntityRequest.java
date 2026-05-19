package ai.claudecode.esgt2.entity.api;

import ai.claudecode.esgt2.entity.domain.LegalEntityType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "법인 등록 요청")
public record CreateEntityRequest(
    @Schema(description = "법인명", example = "삼성전자 주식회사")
    @NotBlank @Size(max = 200) String name,
    @Schema(description = "ISO 3166-1 alpha-2 국가코드", example = "KR")
    @NotNull @Pattern(regexp = "[A-Z]{2}", message = "국가코드는 대문자 2자리여야 합니다.") String countryCode,
    @Schema(description = "법인 유형", example = "PARENT")
    @NotNull LegalEntityType entityType
) {}
