package ai.claudecode.esgt2.rpt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "공시 보고서 생성 요청")
public record CreateReportRequest(
    @Schema(description = "법인 ID") @NotNull UUID entityId,
    @Schema(description = "보고 연도") @NotNull @Min(2020) int reportingYear,
    @Schema(description = "프레임워크 (KSSB2)") String framework
) {
    public CreateReportRequest {
        if (framework == null || framework.isBlank()) framework = "KSSB2";
    }
}
