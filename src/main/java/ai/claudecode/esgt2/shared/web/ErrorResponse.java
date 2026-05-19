package ai.claudecode.esgt2.shared.web;

import java.time.OffsetDateTime;

public record ErrorResponse(String code, String message, OffsetDateTime timestamp) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, OffsetDateTime.now());
    }
}
