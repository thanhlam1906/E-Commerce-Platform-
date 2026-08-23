package com.voltstack.ecommerce.order.exception;

import com.voltstack.ecommerce.order.constant.ErrorMessages;
import com.voltstack.ecommerce.order.dto.response.ApiDataResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiDataResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiDataResponse.error(404, ex.getMessage(), null));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiDataResponse<Void>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiDataResponse.error(400, ex.getMessage(), null));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiDataResponse<Void>> handleDuplicate(DuplicateResourceException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiDataResponse.error(409, ex.getMessage(), null));
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    public ResponseEntity<ApiDataResponse<Void>> handleInvalidState(InvalidOrderStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiDataResponse.error(409, ex.getMessage(), null));
    }

    @ExceptionHandler(CheckoutInProgressException.class)
    public ResponseEntity<ApiDataResponse<Void>> handleCheckoutInProgress(CheckoutInProgressException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiDataResponse.error(409, ex.getMessage(), null));
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiDataResponse<Map<String, List<String>>>> handleOutOfStock(InsufficientStockException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiDataResponse.error(409, ex.getMessage(), null,
                        Map.of("outOfStockSkus", ex.getOutOfStockSkus())));
    }

    @ExceptionHandler(SkuNotFoundException.class)
    public ResponseEntity<ApiDataResponse<Void>> handleSkuNotFound(SkuNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiDataResponse.error(409, ex.getMessage(), null));
    }

    @ExceptionHandler({PaymentUnavailableException.class, ProductUnavailableException.class})
    public ResponseEntity<ApiDataResponse<Void>> handleUnavailable(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiDataResponse.error(503, ex.getMessage(), null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiDataResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiDataResponse.error(400, ErrorMessages.VALIDATION_FAILED, null));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiDataResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiDataResponse.error(409, "Dữ liệu trùng lặp", null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiDataResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiDataResponse.error(403, "Bạn không có quyền truy cập", null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiDataResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiDataResponse.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> ApiDataResponse.FieldError.builder()
                        .field(fieldError.getField())
                        .message(fieldError.getDefaultMessage())
                        .build())
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiDataResponse.error(400, ErrorMessages.VALIDATION_FAILED, errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiDataResponse<Void>> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiDataResponse.error(500, ErrorMessages.INTERNAL_ERROR, null));
    }
}
