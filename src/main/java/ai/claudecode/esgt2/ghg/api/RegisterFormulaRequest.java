package ai.claudecode.esgt2.ghg.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "산식 YAML 등록 요청")
public record RegisterFormulaRequest(
    @Schema(description = "산식 YAML 전체 내용 (test_cases 포함 필수)")
    @NotBlank String yamlContent
) {}
