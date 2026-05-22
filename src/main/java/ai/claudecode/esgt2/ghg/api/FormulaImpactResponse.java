package ai.claudecode.esgt2.ghg.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "산식 변경 영향 조회 결과")
public record FormulaImpactResponse(
    @Schema(description = "산식 코드") String formulaCode,
    @Schema(description = "버전") String formulaVersion,
    @Schema(description = "적용 GHG 카테고리") String ghgCategory,
    @Schema(description = "영향받는 활동 데이터 건수") long affectedActivityDataCount,
    @Schema(description = "영향받는 법인 ID 목록") List<UUID> affectedEntityIds
) {}
