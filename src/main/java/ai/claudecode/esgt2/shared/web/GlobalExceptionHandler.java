package ai.claudecode.esgt2.shared.web;

import ai.claudecode.esgt2.shared.exception.EsgException;
import ai.claudecode.esgt2.shared.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException e) {
        return ResponseEntity.status(404)
            .body(ErrorResponse.of("RESOURCE_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(403)
            .body(ErrorResponse.of("ACCESS_DENIED", "접근이 거부되었습니다."));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException e) {
        return ResponseEntity.status(409)
            .body(ErrorResponse.of("OPTIMISTIC_LOCK_CONFLICT", "동시 수정 충돌이 발생했습니다. 다시 시도해주세요."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().stream()
            .map(error -> error.getDefaultMessage())
            .findFirst()
            .orElse("입력값이 유효하지 않습니다.");
        return ResponseEntity.status(400)
            .body(ErrorResponse.of("VALIDATION_FAILED", message));
    }

    @ExceptionHandler(EsgException.class)
    public ResponseEntity<ErrorResponse> handleEsgException(EsgException e) {
        HttpStatus status = switch (e.getErrorCode()) {
            case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case VALIDATION_FAILED -> HttpStatus.BAD_REQUEST;
            case OPTIMISTIC_LOCK_CONFLICT -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status)
            .body(ErrorResponse.of(e.getErrorCode().name(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(500)
            .body(ErrorResponse.of("INTERNAL_ERROR", "서버 내부 오류가 발생했습니다."));
    }
}
