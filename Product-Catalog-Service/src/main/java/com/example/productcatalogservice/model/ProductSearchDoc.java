package com.example.productcatalogservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;

/**
 * Doc lưu trong Elasticsearch — chỉ phục vụ search/sort (không phải nguồn dữ liệu chính).
 * Mongo vẫn là write source-of-truth + fallback khi ES down. Chỉ index product đang ACTIVE.
 */
@Document(indexName = "products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchDoc {

    @Id
    private String id;

    @Field(type = FieldType.Text)
    private String name;

    @Field(type = FieldType.Text)
    private String slug;

    @Field(type = FieldType.Text)
    private String description;

    // Keyword (không phải Text) — brand nhiều token như "The North Face" phải match chính xác
    @Field(type = FieldType.Keyword)
    private String brand;

    @Field(type = FieldType.Keyword)
    private String categoryId;

    @Field(type = FieldType.Date)
    private Instant createdAt;

    public static ProductSearchDoc from(Product p) {
        return ProductSearchDoc.builder()
                .id(p.getId())
                .name(p.getName())
                .slug(p.getSlug())
                .description(p.getDescription())
                .brand(p.getBrand())
                .categoryId(p.getCategoryId())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
