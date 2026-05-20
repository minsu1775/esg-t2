package ai.claudecode.esgt2.ghg.internal;

import ai.claudecode.esgt2.ghg.api.CsvUploadResponse;
import ai.claudecode.esgt2.ghg.api.IntakeService;
import ai.claudecode.esgt2.ghg.api.WebhookActivityDataItem;
import ai.claudecode.esgt2.ghg.domain.BulkImportResult;
import ai.claudecode.esgt2.ghg.domain.CsvActivityDataParser;
import ai.claudecode.esgt2.ghg.domain.CsvRow;
import ai.claudecode.esgt2.ghg.domain.ImportRowResult;
import ai.claudecode.esgt2.shared.audit.Auditable;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
class DefaultIntakeService implements IntakeService {

    private final ActivityDataRowImporter rowImporter;

    @Override
    @Transactional
    @Auditable(action = "CSV_UPLOADED")
    public CsvUploadResponse uploadCsv(UUID tenantId, UUID entityId, Resource csvFile) {
        List<CsvRow> rows = CsvActivityDataParser.parse(csvFile);
        BulkImportResult result = rowImporter.importRows(tenantId, entityId, rows);
        return toResponse(result);
    }

    @Override
    @Transactional
    @Auditable(action = "WEBHOOK_DATA_RECEIVED")
    public CsvUploadResponse receiveWebhook(UUID tenantId, List<WebhookActivityDataItem> items) {
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
