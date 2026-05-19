package ai.claudecode.esgt2.shared.exception;

import lombok.Getter;

@Getter
public class EsgException extends RuntimeException {

    private final EsgErrorCode errorCode;

    public EsgException(EsgErrorCode errorCode) {
        super(errorCode.name());
        this.errorCode = errorCode;
    }

    public EsgException(EsgErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
