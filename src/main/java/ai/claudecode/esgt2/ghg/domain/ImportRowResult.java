package ai.claudecode.esgt2.ghg.domain;

import java.util.UUID;

public record ImportRowResult(
    int lineNumber,
    String status,
    String message,
    UUID activityDataId
) {
    public static ImportRowResult success(int lineNumber, UUID activityDataId) {
        return new ImportRowResult(lineNumber, "SUCCESS", null, activityDataId);
    }

    public static ImportRowResult skipped(int lineNumber, String reason) {
        return new ImportRowResult(lineNumber, "SKIPPED", reason, null);
    }

    public static ImportRowResult error(int lineNumber, String reason) {
        return new ImportRowResult(lineNumber, "ERROR", reason, null);
    }
}
