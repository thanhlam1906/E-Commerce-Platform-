package com.example.productcatalogservice.repository;

import com.example.productcatalogservice.model.Category;
import com.example.productcatalogservice.model.enums.CategoryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends MongoRepository<Category, String> {

    List<Category> findAllByStatus(CategoryStatus status);

    Page<Category> findAllByStatus(CategoryStatus status, Pageable pageable);

    boolean existsByNameAndStatus(String name, CategoryStatus status);

    boolean existsBySlugAndStatus(String slug, CategoryStatus status);
}
