package ai.claudecode.esgt2.entity.api;

import ai.claudecode.esgt2.entity.domain.LegalEntityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateEntityRequest(
    @NotBlank @Size(max = 200) String name,
    @NotNull @Pattern(regexp = "[A-Z]{2}", message = "국가코드는 대문자 2자리여야 합니다.") String countryCode,
    @NotNull LegalEntityType entityType
) {}
