package com.example.productcatalogservice.service;

import com.example.productcatalogservice.dto.request.CreateCategoryRequest;
import com.example.productcatalogservice.dto.response.CategoryResponse;
import com.example.productcatalogservice.exception.DuplicateResourceException;
import com.example.productcatalogservice.exception.ResourceNotFoundException;
import com.example.productcatalogservice.mapper.CategoryMapper;
import com.example.productcatalogservice.model.Category;
import com.example.productcatalogservice.model.enums.CategoryStatus;
import com.example.productcatalogservice.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private CategoryMapper categoryMapper;
    @InjectMocks
    private CategoryService categoryService;

    private Category category(String id, CategoryStatus status) {
        return Category.builder().id(id).name("name-" + id).slug("slug-" + id).status(status).build();
    }

    private CreateCategoryRequest request(String name, String slug, String parentId) {
        return CreateCategoryRequest.builder().name(name).slug(slug).parentId(parentId).build();
    }

    @Test
    void findAllCategories_nullStatus_defaultsToActive() {
        Pageable pageable = Pageable.unpaged();
        when(categoryRepository.findAllByStatus(CategoryStatus.ACTIVE, pageable)).thenReturn(Page.empty());

        categoryService.findAllCategories(null, pageable);

        verify(categoryRepository).findAllByStatus(CategoryStatus.ACTIVE, pageable);
    }

    @Test
    void findCategoryById_active_returnsResponse() {
        Category c = category("1", CategoryStatus.ACTIVE);
        when(categoryRepository.findById("1")).thenReturn(Optional.of(c));
        CategoryResponse response = mock(CategoryResponse.class);
        when(categoryMapper.toResponse(c)).thenReturn(response);

        assertEquals(response, categoryService.findCategoryById("1"));
    }

    @Test
    void findCategoryById_inactive_throws() {
        when(categoryRepository.findById("1")).thenReturn(Optional.of(category("1", CategoryStatus.INACTIVE)));

        assertThrows(ResourceNotFoundException.class, () -> categoryService.findCategoryById("1"));
    }

    @Test
    void createCategory_duplicateName_throws() {
        when(categoryRepository.existsByNameAndStatus("x", CategoryStatus.ACTIVE)).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> categoryService.createCategory(request("x", "s", null)));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void createCategory_duplicateSlug_throws() {
        when(categoryRepository.existsByNameAndStatus("x", CategoryStatus.ACTIVE)).thenReturn(false);
        when(categoryRepository.existsBySlugAndStatus("s", CategoryStatus.ACTIVE)).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> categoryService.createCategory(request("x", "s", null)));
    }

    @Test
    void createCategory_parentNotFound_throws() {
        when(categoryRepository.existsByNameAndStatus("x", CategoryStatus.ACTIVE)).thenReturn(false);
        when(categoryRepository.existsBySlugAndStatus("s", CategoryStatus.ACTIVE)).thenReturn(false);
        when(categoryRepository.findById("p")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> categoryService.createCategory(request("x", "s", "p")));
    }

    @Test
    void createCategory_success_saves() {
        CreateCategoryRequest req = request("x", "s", null);
        when(categoryRepository.existsByNameAndStatus("x", CategoryStatus.ACTIVE)).thenReturn(false);
        when(categoryRepository.existsBySlugAndStatus("s", CategoryStatus.ACTIVE)).thenReturn(false);
        Category entity = new Category();
        when(categoryMapper.toEntity(req)).thenReturn(entity);
        when(categoryRepository.save(entity)).thenReturn(entity);
        CategoryResponse response = mock(CategoryResponse.class);
        when(categoryMapper.toResponse(entity)).thenReturn(response);

        assertEquals(response, categoryService.createCategory(req));
        verify(categoryRepository).save(entity);
    }

    @Test
    void updateCategory_selfParent_throws() {
        when(categoryRepository.findById("1")).thenReturn(Optional.of(category("1", CategoryStatus.ACTIVE)));

        assertThrows(IllegalArgumentException.class,
                () -> categoryService.updateCategory("1", request("name-1", "slug-1", "1")));
    }

    @Test
    void updateCategory_nameDuplicate_throws() {
        when(categoryRepository.findById("1")).thenReturn(Optional.of(category("1", CategoryStatus.ACTIVE)));
        when(categoryRepository.existsByNameAndStatus("new", CategoryStatus.ACTIVE)).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> categoryService.updateCategory("1", request("new", "slug-1", null)));
    }

    @Test
    void deleteCategory_hasChildren_throws() {
        when(categoryRepository.findById("1")).thenReturn(Optional.of(category("1", CategoryStatus.ACTIVE)));
        when(categoryRepository.countByParentIdAndStatus("1", CategoryStatus.ACTIVE)).thenReturn(2L);

        assertThrows(IllegalArgumentException.class, () -> categoryService.deleteCategory("1"));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void deleteCategory_success_setsInactive() {
        Category c = category("1", CategoryStatus.ACTIVE);
        when(categoryRepository.findById("1")).thenReturn(Optional.of(c));
        when(categoryRepository.countByParentIdAndStatus("1", CategoryStatus.ACTIVE)).thenReturn(0L);

        categoryService.deleteCategory("1");

        assertEquals(CategoryStatus.INACTIVE, c.getStatus());
        verify(categoryRepository).save(c);
    }
}
