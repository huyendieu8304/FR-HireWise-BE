package com.hirewise.be.exception;

import com.hirewise.be.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Catches every exception at the controller layer and returns a single,
 * consistent {@link ErrorResponse} format, with i18n message support via
 * {@code messages.properties}.
 * <p>
 * This is also where {@code CustomAuthenticationEntryPoint} and
 * {@code CustomAccessDeniedHandler} (in the {@code security} package)
 * delegate to, so that 401/403 errors raised by the Spring Security filter
 * chain (before the request even reaches a controller) get the same JSON
 * format as errors raised inside a controller/service.
 * <p>
 * This class is also the single place where errors get logged (using
 * Lombok's {@code @Slf4j} instead of a manually declared Logger) - per
 * project convention, service/use-case code does NOT log again when
 * throwing a business exception; this advice logs it once, here (see
 * {@code 06-LOGGING_CONVENTION.md}).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(BaseException ex, HttpServletRequest request) {
        Locale locale = LocaleContextHolder.getLocale();
        String detailMessage;
        try {
            detailMessage = messageSource.getMessage(ex.getErrorCode().getKey(), ex.getArgs(), locale);
        } catch (Exception e) {
            detailMessage = ex.getErrorCode().name();
        }

        // WARN, not ERROR: this is an expected business exception
        // (404/409/403...) - the app is working as intended. userId is
        // already available in the MDC (UserContextMdcFilter), so there's
        // no need to log them again here. The concrete exception class name
        // (e.g. PermissionDeniedException, OutOfScopeException...) is only
        // used for internal RBAC audit; the client always sees the same
        // generic ErrorCode (BR-RBAC-03), so no internal detail leaks out.
        log.warn("Business exception [{}] ({}) at {}: {}", ex.getErrorCode().name(),
                ex.getClass().getSimpleName(), request.getRequestURI(), detailMessage);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now().toString())
                .status(ex.getStatus().value())
                .code(ex.getErrorCode().name())
                .message(detailMessage)
                .path(request.getRequestURI())
                .build();

        return new ResponseEntity<>(errorResponse, ex.getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        Locale locale = LocaleContextHolder.getLocale();

        String detailMessage = messageSource.getMessage(ErrorCode.VALIDATION_FAILED.getKey(), null, "Validation failed", locale);

        // DEBUG: bean-validation failures are a routine, high-volume, expected
        // client mistake, not something ops needs to act on - so it stays
        // below WARN.
        log.debug("Validation failed at {}: {}", request.getRequestURI(), ex.getBindingResult().getFieldErrorCount());

        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now().toString())
                .status(status.value())
                .code(ErrorCode.VALIDATION_FAILED.name())
                .message(detailMessage)
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .build();

        return new ResponseEntity<>(errorResponse, status);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        // DEBUG: a malformed JSON body is a client-side mistake, not a
        // service issue - same reasoning as the validation handler above.
        log.debug("Malformed request body at {}", request.getRequestURI());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now().toString())
                .status(status.value())
                .code(ErrorCode.INVALID_INPUT.name())
                .message("Malformed JSON request or invalid field format")
                .path(request.getRequestURI())
                .build();
        return new ResponseEntity<>(errorResponse, status);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String message = String.format("Parameter '%s' has invalid value: %s", ex.getName(), ex.getValue());
        // DEBUG: an invalid query/path parameter type is a client input
        // error, same reasoning as the other 400-level handlers above.
        log.debug("Invalid request parameter at {}: {}", request.getRequestURI(), message);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now().toString())
                .status(status.value())
                .code(ErrorCode.INVALID_INPUT.name())
                .message(message)
                .path(request.getRequestURI())
                .build();
        return new ResponseEntity<>(errorResponse, status);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.FORBIDDEN;
        Locale locale = LocaleContextHolder.getLocale();
        String detailMessage = messageSource.getMessage(ErrorCode.FORBIDDEN.getKey(), null, "Access denied", locale);

        // WARN: the user authenticated successfully but lacks the required
        // permission - worth flagging for security/audit purposes, but it's
        // not a server error.
        log.warn("Access denied at {}", request.getRequestURI());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now().toString())
                .status(status.value())
                .code(ErrorCode.FORBIDDEN.name())
                .message(detailMessage)
                .path(request.getRequestURI())
                .build();

        return new ResponseEntity<>(errorResponse, status);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        Locale locale = LocaleContextHolder.getLocale();
        String detailMessage = messageSource.getMessage(ErrorCode.UNAUTHORIZED.getKey(), null, "Unauthorized", locale);

        // WARN: the request failed authentication (missing/invalid
        // credentials) - same security-audit reasoning as access-denied
        // above, not a server error.
        log.warn("Unauthenticated request at {}: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now().toString())
                .status(status.value())
                .code(ErrorCode.UNAUTHORIZED.name())
                .message(detailMessage)
                .path(request.getRequestURI())
                .build();

        return new ResponseEntity<>(errorResponse, status);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(NoResourceFoundException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        Locale locale = LocaleContextHolder.getLocale();
        String detailMessage = messageSource.getMessage(ErrorCode.RESOURCE_NOT_FOUND.getKey(), new Object[]{ex.getResourcePath()}, "Resource not found", locale);

        // DEBUG: no handler matched the requested path - common, low-value
        // noise (bots, favicon/static asset probes), not actionable.
        log.debug("No handler found for {}", request.getRequestURI());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now().toString())
                .status(status.value())
                .code(ErrorCode.RESOURCE_NOT_FOUND.name())
                .message(detailMessage)
                .path(request.getRequestURI())
                .build();

        return new ResponseEntity<>(errorResponse, status);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllUncaughtException(Exception ex, HttpServletRequest request) {
        // ERROR, with full stack trace: this is a catch-all for exceptions
        // we did NOT anticipate, so it's treated as a potential application
        // bug that needs investigation - unlike the expected business/client
        // errors handled above.
        log.error("Uncaught exception occurred at path: {}", request.getRequestURI(), ex);

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        Locale locale = LocaleContextHolder.getLocale();
        String detailMessage = messageSource.getMessage(ErrorCode.INTERNAL_SERVER_ERROR.getKey(), null, "An unexpected error occurred", locale);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now().toString())
                .status(status.value())
                .code(ErrorCode.INTERNAL_SERVER_ERROR.name())
                .message(detailMessage)
                .path(request.getRequestURI())
                .build();

        return new ResponseEntity<>(errorResponse, status);
    }
}
