package com.voltstack.ecommerce.order.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

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

    public static <T> ApiDataResponse<T> created(T data) {
        return ApiDataResponse.<T>builder()
                .code(201)
                .message("Created")
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

    public static <T> ApiDataResponse<T> error(int code, String message, List<FieldError> errors, T data) {
        return ApiDataResponse.<T>builder()
                .code(code)
                .message(message)
                .data(data)
                .errors(errors)
                .timestamp(Instant.now())
                .build();
    }
}
