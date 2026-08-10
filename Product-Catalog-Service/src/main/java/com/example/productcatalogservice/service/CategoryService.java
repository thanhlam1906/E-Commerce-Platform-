package com.example.productcatalogservice.service;

import com.example.productcatalogservice.constant.ErrorMessages;
import com.example.productcatalogservice.dto.request.CreateCategoryRequest;
import com.example.productcatalogservice.dto.response.CategoryResponse;
import com.example.productcatalogservice.exception.DuplicateResourceException;
import com.example.productcatalogservice.exception.ResourceNotFoundException;
import com.example.productcatalogservice.mapper.CategoryMapper;
import com.example.productcatalogservice.model.Category;
import com.example.productcatalogservice.model.enums.CategoryStatus;
import com.example.productcatalogservice.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    public Page<CategoryResponse> findAllCategories(CategoryStatus status, Pageable pageable) {
        if (status != null) {
            return categoryRepository.findAllByStatus(status, pageable)
                    .map(categoryMapper::toResponse);
        }
        return categoryRepository.findAll(pageable)
                .map(categoryMapper::toResponse);
    }

    public CategoryResponse findCategoryById(String id) {
        Category category = categoryRepository.findById(id)
                .filter(c -> c.getStatus() == CategoryStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.CATEGORY_NOT_FOUND + " với id: " + id));
        return categoryMapper.toResponse(category);
    }

    public CategoryResponse createCategory(CreateCategoryRequest request) {
        if (categoryRepository.existsByNameAndStatus(request.getName(), CategoryStatus.ACTIVE)) {
            throw new DuplicateResourceException(ErrorMessages.CATEGORY_NAME_EXISTS);
        }
        if (categoryRepository.existsBySlugAndStatus(request.getSlug(), CategoryStatus.ACTIVE)) {
            throw new DuplicateResourceException(ErrorMessages.CATEGORY_SLUG_EXISTS);
        }
        Category category = categoryMapper.toEntity(request);
        category.setStatus(CategoryStatus.ACTIVE);
        category.setCreatedAt(Instant.now());
        category.setUpdatedAt(Instant.now());
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    public CategoryResponse updateCategory(String id, CreateCategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .filter(c -> c.getStatus() == CategoryStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.CATEGORY_NOT_FOUND + " với id: " + id));

        if (!category.getName().equals(request.getName())
                && categoryRepository.existsByNameAndStatus(request.getName(), CategoryStatus.ACTIVE)) {
            throw new DuplicateResourceException(ErrorMessages.CATEGORY_NAME_EXISTS);
        }
        if (!category.getSlug().equals(request.getSlug())
                && categoryRepository.existsBySlugAndStatus(request.getSlug(), CategoryStatus.ACTIVE)) {
            throw new DuplicateResourceException(ErrorMessages.CATEGORY_SLUG_EXISTS);
        }

        category.setName(request.getName());
        category.setSlug(request.getSlug());
        category.setParentId(request.getParentId());
        category.setUpdatedAt(Instant.now());
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    public void deleteCategory(String id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.CATEGORY_NOT_FOUND + " với id: " + id));
        category.setStatus(CategoryStatus.INACTIVE);
        category.setUpdatedAt(Instant.now());
        categoryRepository.save(category);
    }
}
