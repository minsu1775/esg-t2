package ai.claudecode.esgt2.ghg.internal;

import ai.claudecode.esgt2.ghg.domain.ActivityData;
import ai.claudecode.esgt2.ghg.domain.BulkImportResult;
import ai.claudecode.esgt2.ghg.domain.CreateActivityDataCommand;
import ai.claudecode.esgt2.ghg.domain.CsvRow;
import ai.claudecode.esgt2.ghg.domain.ImportRowResult;
import ai.claudecode.esgt2.ghg.infra.ActivityDataMapper;
import ai.claudecode.esgt2.ghg.infra.ActivityDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class ActivityDataRowImporter {

    private final ActivityDataRepository activityDataRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    ImportRowResult importRow(UUID tenantId, UUID entityId, CsvRow row) {
        try {
            if (row.reportingYear() <= 0 || row.category() == null || row.category().isBlank()
                    || row.quantity() == null || row.unit() == null || row.countryCode() == null) {
                return ImportRowResult.error(row.lineNumber(),
                    "필수 필드 누락: reporting_year, category, quantity, unit, country_code");
            }
            if (row.quantity().signum() <= 0) {
                return ImportRowResult.error(row.lineNumber(),
                    "quantity는 양수여야 합니다: " + row.quantity());
            }

            boolean duplicate = activityDataRepository
                .existsByTenantIdAndEntityIdAndReportingYearAndCategoryAndSubCategoryAndDataSource(
                    tenantId, entityId,
                    row.reportingYear(), row.category(),
                    row.subCategory(), row.dataSource());
            if (duplicate) {
                log.warn("중복 행 건너뜀: tenantId={}, entityId={}, year={}, category={}, line={}",
                    tenantId, entityId, row.reportingYear(), row.category(), row.lineNumber());
                return ImportRowResult.skipped(row.lineNumber(), "중복 항목");
            }

            var cmd = new CreateActivityDataCommand(
                tenantId, entityId,
                row.reportingYear(), row.category(), row.subCategory(),
                row.quantity(), row.unit(), row.countryCode(),
                row.dataSource(), row.dataQuality(),
                row.lifetimeYears());

            var domain = ActivityData.create(cmd);
            var saved = activityDataRepository.save(ActivityDataMapper.toEntity(domain));
            return ImportRowResult.success(row.lineNumber(), saved.getId());

        } catch (Exception e) {
            log.error("행 처리 오류 line={}: {}", row.lineNumber(), e.getMessage());
            return ImportRowResult.error(row.lineNumber(), e.getMessage());
        }
    }

    BulkImportResult importRows(UUID tenantId, UUID entityId, List<CsvRow> rows) {
        List<ImportRowResult> results = rows.stream()
            .map(row -> importRow(tenantId, entityId, row))
            .toList();
        return BulkImportResult.of(results);
    }
}
