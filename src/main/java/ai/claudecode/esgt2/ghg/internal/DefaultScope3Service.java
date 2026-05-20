package ai.claudecode.esgt2.ghg.internal;

import ai.claudecode.esgt2.ghg.api.EmissionRecordResponse;
import ai.claudecode.esgt2.ghg.api.Scope3CoverageRequest;
import ai.claudecode.esgt2.ghg.api.Scope3CoverageResponse;
import ai.claudecode.esgt2.ghg.api.Scope3Service;
import ai.claudecode.esgt2.ghg.domain.EmissionCalculator;
import ai.claudecode.esgt2.ghg.domain.EmissionFactor;
import ai.claudecode.esgt2.ghg.domain.EmissionFactorResolver;
import ai.claudecode.esgt2.ghg.domain.EmissionRecord;
import ai.claudecode.esgt2.ghg.domain.Scope3Cat1Calculator;
import ai.claudecode.esgt2.ghg.domain.Scope3Cat2Calculator;
import ai.claudecode.esgt2.ghg.domain.Scope3Cat11Calculator;
import ai.claudecode.esgt2.ghg.domain.Scope3CoverageCalculator;
import ai.claudecode.esgt2.ghg.domain.Scope3CoverageReport;
import ai.claudecode.esgt2.ghg.infra.ActivityDataJpaEntity;
import ai.claudecode.esgt2.ghg.infra.ActivityDataRepository;
import ai.claudecode.esgt2.ghg.infra.EmissionRecordJpaEntity;
import ai.claudecode.esgt2.ghg.infra.EmissionRecordMapper;
import ai.claudecode.esgt2.ghg.infra.EmissionRecordRepository;
import ai.claudecode.esgt2.ghg.infra.Scope3CoverageReportJpaEntity;
import ai.claudecode.esgt2.ghg.infra.Scope3CoverageReportRepository;
import ai.claudecode.esgt2.shared.audit.Auditable;
import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
class DefaultScope3Service implements Scope3Service {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ActivityDataRepository activityDataRepository;
    private final EmissionRecordRepository emissionRecordRepository;
    private final Scope3CoverageReportRepository coverageReportRepository;
    private final EmissionFactorResolver emissionFactorResolver;

    @Override
    @Transactional
    @Auditable(action = "SCOPE3_CAT1_CALCULATED")
    public List<EmissionRecordResponse> calculateCat1(UUID tenantId, UUID entityId, int reportingYear) {
        return calculateScope3(tenantId, entityId, reportingYear, List.of("SCOPE3_CAT1"), 1);
    }

    @Override
    @Transactional
    @Auditable(action = "SCOPE3_CAT2_CALCULATED")
    public List<EmissionRecordResponse> calculateCat2(UUID tenantId, UUID entityId, int reportingYear) {
        return calculateScope3(tenantId, entityId, reportingYear, List.of("SCOPE3_CAT2"), 2);
    }

    @Override
    @Transactional
    @Auditable(action = "SCOPE3_CAT11_CALCULATED")
    public List<EmissionRecordResponse> calculateCat11(UUID tenantId, UUID entityId, int reportingYear) {
        var activityDataList = activityDataRepository
            .findByTenantIdAndEntityIdAndReportingYearAndCategoryIn(
                tenantId, entityId, reportingYear, List.of("SCOPE3_CAT11"));

        Map<String, EmissionFactor> factorCache = new HashMap<>();
        return activityDataList.stream().map(ad -> {
            var date = LocalDate.of(ad.getReportingYear(), 1, 1);
            var cacheKey = ad.getCategory() + "|" + ad.getSubCategory() + "|" + ad.getCountryCode() + "|" + date;
            var factor = factorCache.computeIfAbsent(cacheKey, k ->
                emissionFactorResolver.resolveAt(ad.getCategory(), ad.getSubCategory(),
                    ad.getCountryCode(), date));

            BigDecimal emission = Scope3Cat11Calculator.computeAnnualEmission(
                ad.getQuantity(), factor.factorValue(), ad.getLifetimeYears());

            var domain = EmissionRecord.calculate(
                ad.getTenantId(), ad.getEntityId(), ad.getId(),
                ad.getReportingYear(), "SCOPE3", 11, "CO2E",
                factor.id(), emission);
            var saved = emissionRecordRepository.save(EmissionRecordMapper.toEntity(domain));
            return toEmissionRecordResponse(saved);
        }).toList();
    }

    @Override
    @Transactional
    @Auditable(action = "SCOPE3_COVERAGE_GENERATED")
    public Scope3CoverageResponse generateCoverageReport(UUID tenantId, UUID entityId,
            int reportingYear, Scope3CoverageRequest request) {

        List<EmissionRecordJpaEntity> scope3Records =
            emissionRecordRepository.findScope3ByTenantIdAndEntityIdAndReportingYear(
                tenantId, entityId, reportingYear);

        // 카테고리별 배출량 합산
        Map<Integer, BigDecimal> includedEmissions = scope3Records.stream()
            .filter(r -> r.getScope3Category() != null)
            .collect(Collectors.groupingBy(
                EmissionRecordJpaEntity::getScope3Category,
                Collectors.reducing(BigDecimal.ZERO,
                    EmissionRecordJpaEntity::getRawEmission, BigDecimal::add)));

        Scope3CoverageReport domain = Scope3CoverageCalculator.calculate(
            tenantId, entityId, reportingYear,
            includedEmissions,
            request.estimatedExcludedEmissions(),
            request.exclusionReasons());

        var entity = Scope3CoverageReportJpaEntity.builder()
            .id(domain.id())
            .tenantId(domain.tenantId())
            .entityId(domain.entityId())
            .reportingYear(domain.reportingYear())
            .includedCategories(toJson(domain.includedCategories()))
            .excludedCategories(domain.excludedCategories().isEmpty()
                ? null : toJson(domain.excludedCategories()))
            .exclusionReasons(domain.exclusionReasons().isEmpty()
                ? null : toJson(domain.exclusionReasons()))
            .coveragePct(domain.coveragePct())
            .meets95PctThreshold(domain.meets95PctThreshold())
            .build();

        var saved = coverageReportRepository.save(entity);
        return toCoverageResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Scope3CoverageResponse getCoverageReport(UUID tenantId, UUID entityId, int reportingYear) {
        return coverageReportRepository
            .findTopByTenantIdAndEntityIdAndReportingYearOrderByGeneratedAtDesc(
                tenantId, entityId, reportingYear)
            .map(this::toCoverageResponse)
            .orElseThrow(() -> new EsgException(EsgErrorCode.RESOURCE_NOT_FOUND,
                "커버리지 보고서를 찾을 수 없습니다: entityId=" + entityId + ", year=" + reportingYear));
    }

    // Cat.1/Cat.2 공통 계산 흐름
    private List<EmissionRecordResponse> calculateScope3(UUID tenantId, UUID entityId,
            int reportingYear, List<String> categories, int scope3CategoryNum) {

        var activityDataList = activityDataRepository
            .findByTenantIdAndEntityIdAndReportingYearAndCategoryIn(
                tenantId, entityId, reportingYear, categories);

        Map<String, EmissionFactor> factorCache = new HashMap<>();
        return activityDataList.stream().map(ad -> {
            var date = LocalDate.of(ad.getReportingYear(), 1, 1);
            var cacheKey = ad.getCategory() + "|" + ad.getSubCategory() + "|" + ad.getCountryCode() + "|" + date;
            var factor = factorCache.computeIfAbsent(cacheKey, k ->
                emissionFactorResolver.resolveAt(ad.getCategory(), ad.getSubCategory(),
                    ad.getCountryCode(), date));

            var domain = EmissionRecord.calculate(
                ad.getTenantId(), ad.getEntityId(), ad.getId(),
                ad.getReportingYear(), "SCOPE3", scope3CategoryNum, "CO2E",
                factor.id(), computeEmission(ad, factor, scope3CategoryNum));
            var saved = emissionRecordRepository.save(EmissionRecordMapper.toEntity(domain));
            return toEmissionRecordResponse(saved);
        }).toList();
    }

    private BigDecimal computeEmission(ActivityDataJpaEntity ad, EmissionFactor factor, int scope3CategoryNum) {
        return switch (scope3CategoryNum) {
            case 1  -> Scope3Cat1Calculator.computeEmission(ad.getQuantity(), factor.factorValue());
            case 2  -> Scope3Cat2Calculator.computeEmission(ad.getQuantity(), factor.factorValue());
            default -> EmissionCalculator.computeEmission(ad.getQuantity(), factor.factorValue());
        };
    }

    private EmissionRecordResponse toEmissionRecordResponse(EmissionRecordJpaEntity e) {
        return new EmissionRecordResponse(
            e.getId(), e.getEntityId(),
            e.getActivityDataId(), e.getReportingYear(),
            e.getScope(), e.getGhgType(), e.getEmissionFactorId(),
            e.getRawEmission(), e.isConsolidated(), e.getCalculatedAt());
    }

    private Scope3CoverageResponse toCoverageResponse(Scope3CoverageReportJpaEntity e) {
        return new Scope3CoverageResponse(
            e.getId(), e.getEntityId(), e.getReportingYear(),
            fromJson(e.getIncludedCategories(), Integer.class),
            e.getExcludedCategories() == null ? List.of()
                : fromJson(e.getExcludedCategories(), Integer.class),
            e.getExclusionReasons() == null ? Map.of()
                : fromJsonMap(e.getExclusionReasons()),
            e.getCoveragePct(), e.isMeets95PctThreshold(), e.getGeneratedAt());
    }

    private String toJson(Object value) {
        try { return OBJECT_MAPPER.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }

    private <T> List<T> fromJson(String json, Class<T> elementType) {
        try {
            var type = OBJECT_MAPPER.getTypeFactory()
                .constructCollectionType(List.class, elementType);
            return OBJECT_MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) { return List.of(); }
    }

    private Map<Integer, String> fromJsonMap(String json) {
        try {
            var type = OBJECT_MAPPER.getTypeFactory()
                .constructMapType(Map.class, Integer.class, String.class);
            return OBJECT_MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) { return Map.of(); }
    }
}
