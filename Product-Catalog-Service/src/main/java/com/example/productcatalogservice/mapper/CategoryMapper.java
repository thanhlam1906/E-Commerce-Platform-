package com.example.productcatalogservice.mapper;

import com.example.productcatalogservice.dto.request.CreateCategoryRequest;
import com.example.productcatalogservice.dto.response.CategoryResponse;
import com.example.productcatalogservice.model.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    CategoryResponse toResponse(Category category);

    List<CategoryResponse> toResponseList(List<Category> categories);

    @Mapping(target = "id", ignore = true)
    Category toEntity(CreateCategoryRequest request);
}
