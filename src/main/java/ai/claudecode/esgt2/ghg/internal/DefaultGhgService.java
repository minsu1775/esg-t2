package ai.claudecode.esgt2.ghg.internal;

import ai.claudecode.esgt2.ghg.api.ActivityDataResponse;
import ai.claudecode.esgt2.ghg.api.CreateActivityDataRequest;
import ai.claudecode.esgt2.ghg.api.EmissionRecordResponse;
import ai.claudecode.esgt2.ghg.api.GhgService;
import ai.claudecode.esgt2.ghg.domain.ActivityData;
import ai.claudecode.esgt2.ghg.domain.CreateActivityDataCommand;
import ai.claudecode.esgt2.ghg.domain.EmissionCalculator;
import ai.claudecode.esgt2.ghg.domain.EmissionFactorResolver;
import ai.claudecode.esgt2.ghg.infra.ActivityDataJpaEntity;
import ai.claudecode.esgt2.ghg.infra.ActivityDataMapper;
import ai.claudecode.esgt2.ghg.infra.ActivityDataRepository;
import ai.claudecode.esgt2.ghg.infra.EmissionRecordJpaEntity;
import ai.claudecode.esgt2.ghg.infra.EmissionRecordRepository;
import ai.claudecode.esgt2.shared.audit.Auditable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class DefaultGhgService implements GhgService {

    private final ActivityDataRepository activityDataRepository;
    private final EmissionRecordRepository emissionRecordRepository;
    private final EmissionFactorResolver emissionFactorResolver;

    @Override
    @Transactional
    @Auditable(action = "ACTIVITY_DATA_CREATED")
    public ActivityDataResponse createActivityData(UUID tenantId, UUID entityId, CreateActivityDataRequest request) {
        var cmd = new CreateActivityDataCommand(
            tenantId, entityId,
            request.reportingYear(), request.category(), request.subCategory(),
            request.quantity(), request.unit(), request.countryCode(),
            request.dataSource(), request.dataQuality());

        ActivityData domain = ActivityData.create(cmd);
        var saved = activityDataRepository.save(ActivityDataMapper.toEntity(domain));
        return toActivityDataResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivityDataResponse> findActivityData(UUID tenantId, UUID entityId, int reportingYear) {
        return activityDataRepository.findByTenantIdAndEntityIdAndReportingYear(tenantId, entityId, reportingYear)
            .stream().map(this::toActivityDataResponse).toList();
    }

    @Override
    @Transactional
    @Auditable(action = "EMISSIONS_CALCULATED")
    public List<EmissionRecordResponse> calculateEmissions(UUID tenantId, UUID entityId, int reportingYear) {
        var activityDataList = activityDataRepository.findByTenantIdAndEntityIdAndReportingYear(
            tenantId, entityId, reportingYear);

        return activityDataList.stream().map(ad -> {
            var date = LocalDate.of(ad.getReportingYear(), 1, 1);
            var factor = emissionFactorResolver.resolveAt(
                ad.getCategory(), ad.getSubCategory(), ad.getCountryCode(), date);
            BigDecimal emission = EmissionCalculator.computeEmission(ad.getQuantity(), factor.factorValue());
            String scope = deriveScopeFromCategory(ad.getCategory());

            var record = EmissionRecordJpaEntity.builder()
                .tenantId(ad.getTenantId())
                .entityId(ad.getEntityId())
                .activityDataId(ad.getId())
                .reportingYear(ad.getReportingYear())
                .scope(scope)
                .ghgType("CO2E")
                .emissionFactorId(factor.id())
                .rawEmission(emission)
                .build();

            var saved = emissionRecordRepository.save(record);
            return toEmissionRecordResponse(saved);
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmissionRecordResponse> findEmissionRecords(UUID tenantId, UUID entityId, int reportingYear) {
        return emissionRecordRepository.findByTenantIdAndEntityIdAndReportingYear(tenantId, entityId, reportingYear)
            .stream().map(this::toEmissionRecordResponse).toList();
    }

    private String deriveScopeFromCategory(String category) {
        if (category == null) return "SCOPE1";
        if (category.startsWith("SCOPE2")) return "SCOPE2_LB";
        if (category.startsWith("SCOPE3")) return "SCOPE3";
        return "SCOPE1";
    }

    private ActivityDataResponse toActivityDataResponse(ActivityDataJpaEntity e) {
        return new ActivityDataResponse(
            e.getId(), e.getTenantId(), e.getEntityId(),
            e.getReportingYear(), e.getCategory(), e.getSubCategory(),
            e.getQuantity(), e.getUnit(), e.getCountryCode(),
            e.getDataSource(), e.getDataQuality(), e.getStatus(),
            e.getCreatedAt());
    }

    private EmissionRecordResponse toEmissionRecordResponse(EmissionRecordJpaEntity e) {
        return new EmissionRecordResponse(
            e.getId(), e.getTenantId(), e.getEntityId(),
            e.getActivityDataId(), e.getReportingYear(),
            e.getScope(), e.getGhgType(), e.getEmissionFactorId(),
            e.getRawEmission(), e.isConsolidated(), e.getCalculatedAt());
    }
}
