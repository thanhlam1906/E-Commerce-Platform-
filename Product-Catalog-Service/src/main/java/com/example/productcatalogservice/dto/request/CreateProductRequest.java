package com.example.productcatalogservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class CreateProductRequest {

    @NotBlank(message = "Tên sản phẩm không được để trống")
    private String name;

    @Size(max = 5000, message = "Mô tả sản phẩm không được vượt quá 5000 ký tự")
    private String description;

    private String categoryId;

    @Size(max = 250, message = "Thương hiệu không được vượt quá 250 ký tự")
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
    }
}
