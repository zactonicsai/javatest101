package com.example.tdd.api;

import com.example.tdd.api.dto.ApiError;
import com.example.tdd.domain.exception.NotFoundException;
import com.example.tdd.domain.exception.ValidationException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError onValidation(MethodArgumentNotValidException e) {
        List<ApiError.FieldError> errors = e.getBindingResult().getFieldErrors().stream()
            .map(fe -> new ApiError.FieldError(
                fe.getField(),
                fe.getDefaultMessage(),
                String.valueOf(fe.getRejectedValue())))
            .toList();
        return new ApiError("VALIDATION_FAILED", "Request body invalid", errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError onConstraint(ConstraintViolationException e) {
        List<ApiError.FieldError> errors = e.getConstraintViolations().stream()
            .map(cv -> new ApiError.FieldError(
                cv.getPropertyPath().toString(),
                cv.getMessage(),
                String.valueOf(cv.getInvalidValue())))
            .toList();
        return new ApiError("VALIDATION_FAILED", "Parameter invalid", errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError onUnreadable(HttpMessageNotReadableException e) {
        return new ApiError("MALFORMED_REQUEST", "Request body could not be parsed");
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError onNotFound(NotFoundException e) {
        return new ApiError("NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ApiError onBusinessValidation(ValidationException e) {
        return new ApiError("BUSINESS_RULE_VIOLATED", e.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError onIntegrity(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        if (message != null && message.contains("idempotency_key")) {
            return new ApiError("DUPLICATE", "Request already processed");
        }
        return new ApiError("CONFLICT", "Resource already exists");
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError onOptimisticLock(ObjectOptimisticLockingFailureException e) {
        return new ApiError("CONCURRENT_MODIFICATION",
            "Resource was modified by another request — please retry");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError onUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return new ApiError("INTERNAL_ERROR", "An unexpected error occurred");
    }
}
