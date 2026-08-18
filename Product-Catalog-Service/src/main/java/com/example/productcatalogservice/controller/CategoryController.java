package com.example.productcatalogservice.controller;

import com.example.productcatalogservice.dto.request.CreateCategoryRequest;
import com.example.productcatalogservice.dto.response.ApiDataResponse;
import com.example.productcatalogservice.dto.response.CategoryResponse;
import com.example.productcatalogservice.model.enums.CategoryStatus;
import com.example.productcatalogservice.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<ApiDataResponse<Page<CategoryResponse>>> findAllCategories(
            @RequestParam(required = false) CategoryStatus status,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiDataResponse.ok(categoryService.findAllCategories(status, pageable)));
    }

    @GetMapping("/inactive")
    @PreAuthorize("hasAnyRole('PRODUCT_ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiDataResponse<Page<CategoryResponse>>> findInactiveCategories(
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiDataResponse.ok(categoryService.findInactiveCategories(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiDataResponse<CategoryResponse>> findCategoryById(@PathVariable String id) {
        return ResponseEntity.ok(ApiDataResponse.ok(categoryService.findCategoryById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PRODUCT_ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiDataResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse response = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiDataResponse.created(response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PRODUCT_ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiDataResponse<CategoryResponse>> updateCategory(
            @PathVariable String id,
            @Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.ok(ApiDataResponse.ok(categoryService.updateCategory(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PRODUCT_ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiDataResponse<Void>> deleteCategory(@PathVariable String id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(ApiDataResponse.ok(null));
    }
}
