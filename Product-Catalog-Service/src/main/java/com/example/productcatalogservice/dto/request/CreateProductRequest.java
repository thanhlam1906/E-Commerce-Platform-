package com.example.productcatalogservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class CreateProductRequest {

    @NotBlank(message = "Tên sản phẩm không được để trống")
    private String name;

    private String description;

    private String categoryId;

    private String brand;

    @NotEmpty(message = "Sản phẩm phải có ít nhất một biến thể")
    @Valid
    private List<Variant> variants;

    @Data
    public static class Variant {

        @NotBlank(message = "SKU không được để trống")
        private String sku;

        @NotBlank(message = "Tên biến thể không được để trống")
        private String name;

        @NotNull(message = "Giá không được để trống")
        @Positive(message = "Giá phải lớn hơn 0")
        private BigDecimal price;

        private Map<String, String> attributes;

        private List<String> images;

        @PositiveOrZero(message = "Số lượng tồn kho phải >= 0")
        private Integer stockQuantity;
    }
}
