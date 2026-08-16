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
 * Bắt toàn bộ exception ở tầng controller và trả về 1 format ErrorResponse
 * thống nhất, có hỗ trợ i18n message qua messages.properties.
 *
 * Đây cũng là nơi CustomAuthenticationEntryPoint / CustomAccessDeniedHandler
 * (trong package security) uỷ quyền lại xử lý, để lỗi 401/403 phát sinh từ
 * Spring Security filter chain (trước khi vào tới controller) cũng có cùng
 * format JSON với lỗi phát sinh trong controller/service.
 *
 * Đây cũng chính là nơi TẬP TRUNG log lỗi (dùng @Slf4j của Lombok thay vì
 * khai báo Logger thủ công) - theo convention của dự án, code ở tầng
 * service/usecase KHÔNG cần tự log lại khi throw exception nghiệp vụ,
 * advice này đã log thay (xem LOGGING_CONVENTION.md).
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

        // WARN, khong phai ERROR: day la exception nghiep vu da luong truoc
        // (404/409/403...), app van hoat dong binh thuong. userId/userRoles da
        // co san trong MDC (UserContextMdcFilter) nen khong can log lai o day.
        log.warn("Business exception [{}] at {}: {}", ex.getErrorCode().name(), request.getRequestURI(), detailMessage);

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

        // WARN: user da xac thuc thanh cong nhung khong du quyen - dang chu y
        // ve mat bao mat/audit, nhung khong phai loi he thong.
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
