package com.voltstack.ecommerce.payment.exception;

import com.voltstack.ecommerce.payment.dto.ApiDataResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiDataResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiDataResponse.error(404, ex.getMessage(), null));
    }

    @ExceptionHandler(WebhookSignatureException.class)
    public ResponseEntity<ApiDataResponse<Void>> handleBadSignature(WebhookSignatureException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiDataResponse.error(401, ex.getMessage(), null));
    }

    @ExceptionHandler(GatewayUnavailableException.class)
    public ResponseEntity<ApiDataResponse<Void>> handleGateway(GatewayUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiDataResponse.error(502, ex.getMessage(), null));
    }

    @ExceptionHandler(InvalidPaymentStateException.class)
    public ResponseEntity<ApiDataResponse<Void>> handleInvalidState(InvalidPaymentStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiDataResponse.error(409, ex.getMessage(), null));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiDataResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiDataResponse.error(409, "Dữ liệu trùng lặp", null));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiDataResponse<Void>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiDataResponse.error(400, ex.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiDataResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiDataResponse.error(400, "Dữ liệu không hợp lệ", null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiDataResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiDataResponse.error(400, "Dữ liệu không hợp lệ", null));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiDataResponse<Void>> handleBadParams(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiDataResponse.error(400, "Tham số không hợp lệ", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiDataResponse<Void>> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiDataResponse.error(500, "Lỗi hệ thống", null));
    }
}
