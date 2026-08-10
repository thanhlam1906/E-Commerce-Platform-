package com.example.productcatalogservice.model;

import com.example.productcatalogservice.model.enums.CategoryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "categories")
@Data
@Builder
@CompoundIndexes({
    @CompoundIndex(def = "{'name' : 1, 'status' : 1}", unique = true),
    @CompoundIndex(def = "{'slug' : 1, 'status' : 1}", unique = true)
})
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    @Id
    private String id;

    private String name;
    private String slug;
    private String parentId;

    @Builder.Default
    private CategoryStatus status = CategoryStatus.ACTIVE;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();
}
