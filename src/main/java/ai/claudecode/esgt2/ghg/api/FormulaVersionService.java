package ai.claudecode.esgt2.ghg.api;

import java.util.List;
import java.util.UUID;

public interface FormulaVersionService {

    /** 산식 YAML 등록 — test_cases 게이트 통과 시만 ACTIVE (T-6B-07, T-6B-08) */
    FormulaVersionResponse register(UUID actorId, RegisterFormulaRequest request);

    /** 동일 code의 전체 버전 이력 조회 */
    List<FormulaVersionResponse> findAll(String code);

    /** 특정 버전 비활성화 — DELETE 없음, status만 INACTIVE (T-6B-08) */
    FormulaVersionResponse deactivate(UUID actorId, UUID formulaVersionId);

    /** 산식 변경 영향 조회 — ghgCategory에 해당하는 활동 데이터 건수·법인 (T-6B-09) */
    FormulaImpactResponse getImpact(UUID tenantId, UUID formulaVersionId);
}
