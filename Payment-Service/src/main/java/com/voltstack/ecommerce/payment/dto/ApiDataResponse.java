package com.voltstack.ecommerce.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/** Same envelope as Order-Service for consistent user-facing error/responses. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiDataResponse<T> {

    private int code;
    private String message;
    private T data;
    private List<FieldError> errors;
    private Instant timestamp;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldError {
        private String field;
        private String message;
    }

    public static <T> ApiDataResponse<T> ok(T data) {
        return ApiDataResponse.<T>builder()
                .code(200)
                .message("Success")
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiDataResponse<T> error(int code, String message, List<FieldError> errors) {
        return ApiDataResponse.<T>builder()
                .code(code)
                .message(message)
                .errors(errors)
                .timestamp(Instant.now())
                .build();
    }
}
