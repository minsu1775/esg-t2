package ai.claudecode.esgt2.ghg.api;

import java.util.List;
import java.util.UUID;

public interface Scope3Service {

    // Cat.1: 지출 기반 배출량 산출 (entity의 해당 연도 SCOPE3_CAT1 활동 데이터 전체)
    List<EmissionRecordResponse> calculateCat1(UUID tenantId, UUID entityId, int reportingYear);

    // Cat.2: 자본재 배출량 산출 (entity의 해당 연도 SCOPE3_CAT2 활동 데이터 전체)
    List<EmissionRecordResponse> calculateCat2(UUID tenantId, UUID entityId, int reportingYear);

    // Cat.11: 판매제품 사용 배출량 산출 (entity의 해당 연도 SCOPE3_CAT11 활동 데이터 전체)
    List<EmissionRecordResponse> calculateCat11(UUID tenantId, UUID entityId, int reportingYear);

    // 커버리지 보고서 생성 (배출량 기반 95% 임계값 판단)
    Scope3CoverageResponse generateCoverageReport(UUID tenantId, UUID entityId,
        int reportingYear, Scope3CoverageRequest request);

    // 최신 커버리지 보고서 조회
    Scope3CoverageResponse getCoverageReport(UUID tenantId, UUID entityId, int reportingYear);
}
