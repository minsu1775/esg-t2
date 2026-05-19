package ai.claudecode.esgt2.shared.exception;

public class ResourceNotFoundException extends EsgException {

    public ResourceNotFoundException(String message) {
        super(EsgErrorCode.RESOURCE_NOT_FOUND, message);
    }
}
