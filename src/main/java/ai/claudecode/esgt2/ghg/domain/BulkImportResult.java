package ai.claudecode.esgt2.ghg.domain;

import java.util.List;

public record BulkImportResult(
    int totalRows,
    int successCount,
    int skipCount,
    int errorCount,
    List<ImportRowResult> rows
) {
    public static BulkImportResult of(List<ImportRowResult> rows) {
        int success = (int) rows.stream().filter(r -> "SUCCESS".equals(r.status())).count();
        int skipped = (int) rows.stream().filter(r -> "SKIPPED".equals(r.status())).count();
        int error   = (int) rows.stream().filter(r -> "ERROR".equals(r.status())).count();
        return new BulkImportResult(rows.size(), success, skipped, error, List.copyOf(rows));
    }
}
