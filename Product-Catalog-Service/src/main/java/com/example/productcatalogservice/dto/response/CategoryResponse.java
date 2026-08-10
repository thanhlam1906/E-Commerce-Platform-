package com.example.productcatalogservice.dto.response;

import com.example.productcatalogservice.model.enums.CategoryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {

    private String id;
    private String name;
    private String slug;
    private String parentId;
    private CategoryStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
