package ai.claudecode.esgt2.ghg.internal;

import ai.claudecode.esgt2.entity.api.EntityManagementService;
import ai.claudecode.esgt2.ghg.api.CsvUploadResponse;
import ai.claudecode.esgt2.ghg.api.IntakeService;
import ai.claudecode.esgt2.ghg.api.WebhookActivityDataItem;
import ai.claudecode.esgt2.ghg.domain.BulkImportResult;
import ai.claudecode.esgt2.ghg.domain.CsvActivityDataParser;
import ai.claudecode.esgt2.ghg.domain.CsvRow;
import ai.claudecode.esgt2.ghg.domain.ImportRowResult;
import ai.claudecode.esgt2.shared.audit.Auditable;
import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
class DefaultIntakeService implements IntakeService {

    private final ActivityDataRowImporter rowImporter;
    private final EntityManagementService entityManagementService;

    @Override
    @Transactional
    @Auditable(action = "CSV_UPLOADED")
    public CsvUploadResponse uploadCsv(UUID tenantId, UUID entityId, Resource csvFile) {
        // 법인 소속 검증 (Phase 4 ConsolidationService 패턴 동일)
        entityManagementService.findById(tenantId, entityId)
            .orElseThrow(() -> new EsgException(EsgErrorCode.VALIDATION_FAILED,
                "법인을 찾을 수 없거나 접근 권한이 없습니다: " + entityId));

        List<CsvRow> rows;
        try {
            rows = CsvActivityDataParser.parse(csvFile.getInputStream());
        } catch (IllegalArgumentException e) {
            throw new EsgException(EsgErrorCode.CSV_PARSE_FAILED, e.getMessage());
        } catch (IOException e) {
            throw new EsgException(EsgErrorCode.CSV_PARSE_FAILED, "CSV 파일 읽기 실패: " + e.getMessage());
        }
        BulkImportResult result = rowImporter.importRows(tenantId, entityId, rows);
        return toResponse(result);
    }

    @Override
    @Transactional
    @Auditable(action = "WEBHOOK_DATA_RECEIVED")
    public CsvUploadResponse receiveWebhook(UUID tenantId, List<WebhookActivityDataItem> items) {
        // 항목별로 다른 entityId가 올 수 있으므로 고유 entityId 선별 검증
        var invalidEntities = items.stream()
            .map(WebhookActivityDataItem::entityId)
            .distinct()
            .filter(eid -> entityManagementService.findById(tenantId, eid).isEmpty())
            .toList();
        if (!invalidEntities.isEmpty()) {
            throw new EsgException(EsgErrorCode.VALIDATION_FAILED,
                "테넌트에 속하지 않는 법인 ID: " + invalidEntities);
        }

        var counter = new AtomicInteger(1);
        List<ImportRowResult> results = items.stream().map(item -> {
            var row = new CsvRow(
                counter.getAndIncrement(),
                item.reportingYear(), item.category(), item.subCategory(),
                item.quantity(), item.unit(), item.countryCode(),
                item.dataSource() != null ? item.dataSource() : "WEBHOOK",
                item.dataQuality(), item.lifetimeYears());
            return rowImporter.importRow(tenantId, item.entityId(), row);
        }).toList();
        return toResponse(BulkImportResult.of(results));
    }

    private CsvUploadResponse toResponse(BulkImportResult result) {
        var nonSuccess = result.rows().stream()
            .filter(r -> !"SUCCESS".equals(r.status()))
            .map(r -> new CsvUploadResponse.RowResult(r.lineNumber(), r.status(), r.message()))
            .toList();
        return new CsvUploadResponse(
            result.totalRows(), result.successCount(),
            result.skipCount(), result.errorCount(), nonSuccess);
    }
}
