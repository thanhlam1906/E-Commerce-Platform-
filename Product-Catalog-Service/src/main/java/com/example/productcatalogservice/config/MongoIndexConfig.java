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
    @SuppressWarnings("deprecation")
    void ensureIndexes() {
        // SKU unique trên variants.sku (embedded array) — không khai báo được bằng annotation
        mongoTemplate.indexOps("products")
                .ensureIndex(new Index()
                        .on("variants.sku", Sort.Direction.ASC)
                        .unique()
                        .sparse());
    }
}
