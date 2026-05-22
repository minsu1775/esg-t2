package ai.claudecode.esgt2.ghg.internal;

import ai.claudecode.esgt2.ghg.api.ActivityDataDiffResponse;
import ai.claudecode.esgt2.ghg.api.ActivityDataResponse;
import ai.claudecode.esgt2.ghg.api.ActivityDataVersionResponse;
import ai.claudecode.esgt2.ghg.api.CorrectActivityDataRequest;
import ai.claudecode.esgt2.ghg.api.CreateActivityDataRequest;
import ai.claudecode.esgt2.ghg.api.EmissionRecordResponse;
import ai.claudecode.esgt2.ghg.api.GhgService;
import ai.claudecode.esgt2.ghg.domain.ActivityData;
import ai.claudecode.esgt2.ghg.domain.CorrectActivityDataCommand;
import ai.claudecode.esgt2.ghg.domain.CreateActivityDataCommand;
import ai.claudecode.esgt2.ghg.domain.EmissionCalculator;
import ai.claudecode.esgt2.ghg.domain.EmissionFactor;
import ai.claudecode.esgt2.ghg.domain.EmissionFactorResolver;
import ai.claudecode.esgt2.ghg.domain.EmissionRecord;
import ai.claudecode.esgt2.ghg.infra.ActivityDataJpaEntity;
import ai.claudecode.esgt2.ghg.infra.ActivityDataMapper;
import ai.claudecode.esgt2.ghg.infra.ActivityDataRepository;
import ai.claudecode.esgt2.ghg.infra.EmissionRecordJpaEntity;
import ai.claudecode.esgt2.ghg.infra.EmissionRecordMapper;
import ai.claudecode.esgt2.ghg.infra.EmissionRecordRepository;
import ai.claudecode.esgt2.shared.audit.Auditable;
import ai.claudecode.esgt2.shared.event.ActivityDataCorrectedEvent;
import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;
import ai.claudecode.esgt2.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class DefaultGhgService implements GhgService {

    private final ActivityDataRepository activityDataRepository;
    private final EmissionRecordRepository emissionRecordRepository;
    private final EmissionFactorResolver emissionFactorResolver;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    @Auditable(action = "ACTIVITY_DATA_CREATED")
    public ActivityDataResponse createActivityData(UUID tenantId, UUID entityId, CreateActivityDataRequest request) {
        var cmd = new CreateActivityDataCommand(
            tenantId, entityId,
            request.reportingYear(), request.category(), request.subCategory(),
            request.quantity(), request.unit(), request.countryCode(),
            request.dataSource(), request.dataQuality(),
            request.lifetimeYears());

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

        // P2: 배출계수 캐싱으로 동일 카테고리에 대한 반복 DB 조회 방지 (category+sub+country+date → factor)
        Map<String, EmissionFactor> factorCache = new HashMap<>();

        return activityDataList.stream().map(ad -> {
            var date = LocalDate.of(ad.getReportingYear(), 1, 1);
            var cacheKey = ad.getCategory() + "|" + ad.getSubCategory() + "|" + ad.getCountryCode() + "|" + date;
            var factor = factorCache.computeIfAbsent(cacheKey, k ->
                emissionFactorResolver.resolveAt(ad.getCategory(), ad.getSubCategory(), ad.getCountryCode(), date));

            BigDecimal emission = EmissionCalculator.computeEmission(ad.getQuantity(), factor.factorValue());
            String scope = deriveScopeFromCategory(ad.getCategory());

            var domain = EmissionRecord.calculate(
                ad.getTenantId(), ad.getEntityId(), ad.getId(),
                ad.getReportingYear(), scope, null,  // scope3Category: Scope1/2는 null
                "CO2E", factor.id(), emission);
            var saved = emissionRecordRepository.save(EmissionRecordMapper.toEntity(domain));
            return toEmissionRecordResponse(saved);
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmissionRecordResponse> findEmissionRecords(UUID tenantId, UUID entityId, int reportingYear) {
        return emissionRecordRepository.findByTenantIdAndEntityIdAndReportingYear(tenantId, entityId, reportingYear)
            .stream().map(this::toEmissionRecordResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActivityData(UUID tenantId, UUID entityId, int reportingYear) {
        return activityDataRepository.existsByTenantIdAndEntityIdAndReportingYear(
            tenantId, entityId, reportingYear);
    }

    // ── 정정 워크플로우 ────────────────────────────────────────────────────────

    @Override
    @Transactional
    @Auditable(action = "ACTIVITY_DATA_CORRECTED")
    public ActivityDataResponse correctActivityData(
            UUID tenantId, UUID actorId, UUID originalId, CorrectActivityDataRequest request) {

        // 1. 원본 조회 (테넌트 격리 포함)
        var originalEntity = activityDataRepository.findByIdAndTenantId(originalId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("activity_data not found: " + originalId));

        // 2. 커맨드 생성 — correctionReason 검증은 compact constructor에서 수행
        CorrectActivityDataCommand cmd;
        try {
            cmd = new CorrectActivityDataCommand(
                tenantId, originalEntity.getEntityId(),
                request.reportingYear(), request.category(), request.subCategory(),
                request.quantity(), request.unit(), request.countryCode(),
                request.dataSource(), request.dataQuality(), request.lifetimeYears(),
                request.correctionReason());
        } catch (IllegalArgumentException e) {
            throw new EsgException(EsgErrorCode.VALIDATION_FAILED, e.getMessage());
        }

        // 3. 원본 도메인 객체 재구성 (Mapper 역방향 변환)
        var originalDomain = new ActivityData(
            originalEntity.getId(), originalEntity.getTenantId(), originalEntity.getEntityId(),
            originalEntity.getReportingYear(), originalEntity.getCategory(), originalEntity.getSubCategory(),
            originalEntity.getQuantity(), originalEntity.getUnit(), originalEntity.getCountryCode(),
            originalEntity.getDataSource(), originalEntity.getDataQuality(),
            originalEntity.getStandardValue(), originalEntity.getStandardUnit(),
            originalEntity.getLifetimeYears(),
            originalEntity.getCorrectionOf(), originalEntity.getCorrectionReason());

        // 4. 정정 도메인 객체 생성
        var correctedDomain = ActivityData.correct(originalDomain, cmd);

        // 5. 원본 ARCHIVED (P1: 원본 불변 — status만 변경)
        originalEntity.archive();
        activityDataRepository.save(originalEntity);

        // 6. 정정 레코드 저장
        var savedEntity = activityDataRepository.save(ActivityDataMapper.toEntity(correctedDomain));

        // 7. 재산출 이벤트 발행 (T-6B-04: ActivityDataEventHandler가 수신)
        eventPublisher.publishEvent(new ActivityDataCorrectedEvent(
            tenantId, savedEntity.getEntityId(), savedEntity.getReportingYear(), savedEntity.getId()));

        return toActivityDataResponse(savedEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivityDataVersionResponse> findVersionHistory(UUID tenantId, UUID activityDataId) {
        var target = activityDataRepository.findByIdAndTenantId(activityDataId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("activity_data not found: " + activityDataId));

        // 루트 ID: correctionOf가 null이면 이미 루트, 아니면 correctionOf가 루트
        UUID rootId = target.getCorrectionOf() != null ? target.getCorrectionOf() : target.getId();

        var list = new ArrayList<ActivityDataJpaEntity>();
        activityDataRepository.findByIdAndTenantId(rootId, tenantId).ifPresent(list::add);
        list.addAll(activityDataRepository.findByCorrectionOfAndTenantId(rootId, tenantId));

        return list.stream()
            .sorted(Comparator.comparing(ActivityDataJpaEntity::getCreatedAt))
            .map(this::toVersionResponse)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ActivityDataDiffResponse findDiff(UUID tenantId, UUID correctedId) {
        var corrected = activityDataRepository.findByIdAndTenantId(correctedId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("activity_data not found: " + correctedId));
        if (corrected.getCorrectionOf() == null) {
            throw new EsgException(EsgErrorCode.VALIDATION_FAILED, "정정 이력이 없는 데이터입니다");
        }
        var original = activityDataRepository.findByIdAndTenantId(corrected.getCorrectionOf(), tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "원본 activity_data not found: " + corrected.getCorrectionOf()));

        return new ActivityDataDiffResponse(
            original.getId(), corrected.getId(),
            corrected.getCorrectionReason(),
            original.getQuantity(), corrected.getQuantity(),
            original.getUnit(), corrected.getUnit(),
            original.getCategory(), corrected.getCategory(),
            original.getCreatedAt(), corrected.getCreatedAt());
    }

    private String deriveScopeFromCategory(String category) {
        if (category == null) return "SCOPE1";
        // SCOPE2_ELECTRICITY_MB → market-based; SCOPE2_* → location-based
        if (category.endsWith("_MB")) return "SCOPE2_MB";
        if (category.startsWith("SCOPE2")) return "SCOPE2_LB";
        if (category.startsWith("SCOPE3")) return "SCOPE3";
        return "SCOPE1";
    }

    private ActivityDataResponse toActivityDataResponse(ActivityDataJpaEntity e) {
        return new ActivityDataResponse(
            e.getId(), e.getEntityId(),
            e.getReportingYear(), e.getCategory(), e.getSubCategory(),
            e.getQuantity(), e.getUnit(), e.getCountryCode(),
            e.getDataSource(), e.getDataQuality(), e.getStatus(),
            e.getCreatedAt());
    }

    private EmissionRecordResponse toEmissionRecordResponse(EmissionRecordJpaEntity e) {
        return new EmissionRecordResponse(
            e.getId(), e.getEntityId(),
            e.getActivityDataId(), e.getReportingYear(),
            e.getScope(), e.getGhgType(), e.getEmissionFactorId(),
            e.getRawEmission(), e.isConsolidated(), e.getCalculatedAt());
    }

    private ActivityDataVersionResponse toVersionResponse(ActivityDataJpaEntity e) {
        return new ActivityDataVersionResponse(
            e.getId(), e.getCorrectionOf(), e.getCorrectionReason(),
            e.getQuantity(), e.getUnit(), e.getCategory(), e.getSubCategory(),
            e.getStatus(), e.getCreatedAt());
    }
}
