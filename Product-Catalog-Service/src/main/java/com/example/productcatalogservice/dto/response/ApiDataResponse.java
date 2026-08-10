package com.example.productcatalogservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiDataResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private List<FieldError> errors;
    private String httpStatus;
    private Instant timestamp;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldError {
        private String field;
        private String message;
    }

    // ---- Factory methods ----

    public static <T> ApiDataResponse<T> ok(T data) {
        return ApiDataResponse.<T>builder()
                .success(true)
                .message("Thành công")
                .data(data)
                .httpStatus(HttpStatus.OK.name())
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiDataResponse<T> created(T data) {
        return ApiDataResponse.<T>builder()
                .success(true)
                .message("Tạo mới thành công")
                .data(data)
                .httpStatus(HttpStatus.CREATED.name())
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiDataResponse<T> error(HttpStatus status, String message, List<FieldError> errors) {
        return ApiDataResponse.<T>builder()
                .success(false)
                .message(message)
                .errors(errors)
                .httpStatus(status.name())
                .timestamp(Instant.now())
                .build();
    }
}
