package pl.kathelan.user.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.kathelan.common.resilience.exception.CircuitOpenException;
import pl.kathelan.common.api.ApiResponse;

import java.util.stream.Collectors;

@RestControllerAdvice
public class ValidationExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidationErrors(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ApiResponse.error("VALIDATION_ERROR", message);
    }

    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleUserNotFound(UserNotFoundException ex) {
        return ApiResponse.error(ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        return ApiResponse.error(ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(CircuitOpenException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiResponse<Void> handleCircuitOpen(CircuitOpenException ex) {
        return ApiResponse.error("SERVICE_UNAVAILABLE", ex.getMessage());
    }

    @ExceptionHandler(UserServiceException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleUserServiceException(UserServiceException ex) {
        return ApiResponse.error(ex.getErrorCode(), ex.getMessage());
    }
}