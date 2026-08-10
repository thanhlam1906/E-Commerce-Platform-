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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    public Page<CategoryResponse> findAllCategories(CategoryStatus status, Pageable pageable) {
        CategoryStatus effectiveStatus = (status != null) ? status : CategoryStatus.ACTIVE;
        return categoryRepository.findAllByStatus(effectiveStatus, pageable)
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
        validateParentCategory(request.getParentId());

        Category category = categoryMapper.toEntity(request);
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

        if (id.equals(request.getParentId())) {
            throw new IllegalArgumentException("Danh mục không thể là cha của chính nó");
        }
        validateParentCategory(request.getParentId());

        category.setName(request.getName());
        category.setSlug(request.getSlug());
        category.setParentId(request.getParentId());
        category.setUpdatedAt(Instant.now());
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    public void deleteCategory(String id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.CATEGORY_NOT_FOUND + " với id: " + id));

        long childCount = categoryRepository.countByParentIdAndStatus(id, CategoryStatus.ACTIVE);
        if (childCount > 0) {
            throw new IllegalArgumentException("Không thể xóa danh mục đang có " + childCount
                    + " danh mục con. Vui lòng xóa danh mục con trước.");
        }

        category.setStatus(CategoryStatus.INACTIVE);
        category.setUpdatedAt(Instant.now());
        categoryRepository.save(category);
    }

    private void validateParentCategory(String parentId) {
        if (parentId == null) {
            return;
        }
        categoryRepository.findById(parentId)
                .filter(p -> p.getStatus() == CategoryStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Danh mục cha không tìm thấy với id: " + parentId));
    }
}
