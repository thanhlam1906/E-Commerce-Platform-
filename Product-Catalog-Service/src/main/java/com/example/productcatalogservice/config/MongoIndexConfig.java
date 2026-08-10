package com.example.productcatalogservice.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MongoIndexConfig {

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    void ensureIndexes() {
        // SKU unique trên variants.sku (embedded array)
        mongoTemplate.indexOps("products")
                .ensureIndex(new Index()
                        .on("variants.sku", Sort.Direction.ASC)
                        .unique()
                        .sparse());

        // Compound index: findByCategoryId + isActive
        mongoTemplate.indexOps("products")
                .ensureIndex(new Index()
                        .on("isActive", Sort.Direction.ASC)
                        .on("categoryId", Sort.Direction.ASC));

        // Compound index: findAllByIsActive sorted by createdAt
        mongoTemplate.indexOps("products")
                .ensureIndex(new Index()
                        .on("isActive", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.DESC));
    }
}
