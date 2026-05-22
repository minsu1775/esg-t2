package ai.claudecode.esgt2.ghg.internal;

import ai.claudecode.esgt2.ghg.domain.EmissionCalculator;
import ai.claudecode.esgt2.ghg.domain.EmissionFactorResolver;
import ai.claudecode.esgt2.ghg.domain.EmissionRecord;
import ai.claudecode.esgt2.ghg.infra.ActivityDataRepository;
import ai.claudecode.esgt2.ghg.infra.EmissionRecordMapper;
import ai.claudecode.esgt2.ghg.infra.EmissionRecordRepository;
import ai.claudecode.esgt2.shared.event.ActivityDataCorrectedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;

/**
 * 활동 데이터 정정 이벤트 핸들러 — 정정된 레코드에 대해 배출량 재산출.
 * <p>
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}: 정정 트랜잭션 커밋 이후 실행.
 * 이유: EmissionFactorResolver가 @Transactional(readOnly=true)이므로 EsgException 발생 시
 * 동일 트랜잭션을 rollback-only로 마킹 → 예외 캐치만으로는 UnexpectedRollbackException 방지 불가.
 * AFTER_COMMIT + REQUIRES_NEW 조합으로 정정 커밋을 보호하고 재산출을 독립 트랜잭션에서 실행.
 * <p>
 * EmissionRecordRepository는 append-only → 기존 레코드 삭제 없이 새 레코드만 INSERT.
 * <p>
 * T-6B-04
 */
@Component
@RequiredArgsConstructor
@Slf4j
class ActivityDataEventHandler {

    private final ActivityDataRepository activityDataRepository;
    private final EmissionRecordRepository emissionRecordRepository;
    private final EmissionFactorResolver emissionFactorResolver;

    /**
     * 정정 트랜잭션 커밋 이후 새로운 트랜잭션에서 배출량 재산출.
     * 배출계수 미존재 등 재산출 실패는 경고 로그만 남기고 무시.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onActivityDataCorrected(ActivityDataCorrectedEvent event) {
        activityDataRepository.findByIdAndTenantId(event.newActivityDataId(), event.tenantId())
            .ifPresentOrElse(
                ad -> {
                    try {
                        var date = LocalDate.of(ad.getReportingYear(), 1, 1);
                        var factor = emissionFactorResolver.resolveAt(
                            ad.getCategory(), ad.getSubCategory(), ad.getCountryCode(), date);
                        var emission = EmissionCalculator.computeEmission(
                            ad.getQuantity(), factor.factorValue());
                        String scope = deriveScopeFromCategory(ad.getCategory());
                        var domain = EmissionRecord.calculate(
                            event.tenantId(), ad.getEntityId(), ad.getId(),
                            ad.getReportingYear(), scope, null,
                            "CO2E", factor.id(), emission);
                        emissionRecordRepository.save(EmissionRecordMapper.toEntity(domain));
                        log.info("[T-6B-04] 정정 후 배출량 재산출 완료: activityDataId={}", ad.getId());
                    } catch (Exception e) {
                        // 배출계수 없음 등 재산출 실패는 경고만 — 정정은 이미 커밋됨
                        log.warn("[T-6B-04] 정정 후 배출량 재산출 실패: activityDataId={}, reason={}",
                            event.newActivityDataId(), e.getMessage());
                    }
                },
                () -> log.warn("[T-6B-04] 재산출 대상 활동 데이터 없음: id={}", event.newActivityDataId())
            );
    }

    private String deriveScopeFromCategory(String category) {
        if (category == null) return "SCOPE1";
        if (category.endsWith("_MB")) return "SCOPE2_MB";
        if (category.startsWith("SCOPE2")) return "SCOPE2_LB";
        if (category.startsWith("SCOPE3")) return "SCOPE3";
        return "SCOPE1";
    }
}
