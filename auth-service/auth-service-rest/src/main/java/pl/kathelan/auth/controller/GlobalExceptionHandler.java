package pl.kathelan.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.kathelan.auth.api.exception.AuthProcessNotFoundException;
import pl.kathelan.auth.api.exception.InvalidStateTransitionException;
import pl.kathelan.auth.exception.AccountNotEligibleException;
import pl.kathelan.auth.exception.AllDevicesBlockedException;
import pl.kathelan.auth.exception.NoDevicesFoundException;
import pl.kathelan.common.api.ApiResponse;
import pl.kathelan.common.resilience.exception.CircuitOpenException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthProcessNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNotFound(AuthProcessNotFoundException ex) {
        return ApiResponse.error("PROCESS_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleInvalidTransition(InvalidStateTransitionException ex) {
        return ApiResponse.error("INVALID_STATE_TRANSITION", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ApiResponse.error("VALIDATION_ERROR", message);
    }

    @ExceptionHandler(AccountNotEligibleException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ApiResponse<Void> handleAccountNotEligible(AccountNotEligibleException ex) {
        return ApiResponse.error("ACCOUNT_NOT_ELIGIBLE", ex.getMessage());
    }

    @ExceptionHandler(AllDevicesBlockedException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ApiResponse<Void> handleAllDevicesBlocked(AllDevicesBlockedException ex) {
        return ApiResponse.error("ALL_DEVICES_BLOCKED", ex.getMessage());
    }

    @ExceptionHandler(NoDevicesFoundException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ApiResponse<Void> handleNoDevicesFound(NoDevicesFoundException ex) {
        return ApiResponse.error("NO_DEVICES_FOUND", ex.getMessage());
    }

    @ExceptionHandler(CircuitOpenException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiResponse<Void> handleCircuitOpen(CircuitOpenException ex) {
        return ApiResponse.error("SERVICE_UNAVAILABLE", ex.getMessage());
    }
}