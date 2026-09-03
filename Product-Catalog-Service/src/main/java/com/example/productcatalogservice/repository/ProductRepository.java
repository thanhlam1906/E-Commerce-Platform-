package com.example.productcatalogservice.repository;

import com.example.productcatalogservice.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    Page<Product> findAllByIsActiveTrue(Pageable pageable);

    Page<Product> findAllByIsActiveFalse(Pageable pageable);

    Page<Product> findByCategoryIdInAndIsActiveTrue(Collection<String> categoryIds, Pageable pageable);

    Page<Product> findByBrandAndIsActiveTrue(String brand, Pageable pageable);

    Page<Product> findByCategoryIdInAndBrandAndIsActiveTrue(Collection<String> categoryIds, String brand, Pageable pageable);

    @Query("{ $text: { $search: ?0 }, 'isActive': true }")
    Page<Product> searchByKeyword(String keyword, Pageable pageable);

    @Query("{ 'variants.sku': { $in: ?0 } }")
    List<Product> findByVariantsSkuIn(List<String> skus);

    Optional<Product> findByVariantsSku(String sku);
}
