package com.example.productcatalogservice.controller;

import com.example.productcatalogservice.dto.request.CreateProductRequest;
import com.example.productcatalogservice.dto.response.ApiDataResponse;
import com.example.productcatalogservice.dto.response.ProductResponse;
import com.example.productcatalogservice.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('PRODUCT_ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiDataResponse<ProductResponse>> createProduct(
            @RequestPart("product") @Valid CreateProductRequest request,
            MultipartHttpServletRequest httpRequest) {

        Map<Integer, List<MultipartFile>> variantImages = extractVariantImages(httpRequest);
        ProductResponse response = productService.createProduct(request, variantImages);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiDataResponse.created(response));
    }

    @GetMapping
    public ResponseEntity<ApiDataResponse<Page<ProductResponse>>> findAllProducts(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiDataResponse.ok(productService.findAllProducts(categoryId, keyword, pageable)));
    }

    @GetMapping("/inactive")
    @PreAuthorize("hasAnyRole('PRODUCT_ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiDataResponse<Page<ProductResponse>>> findInactiveProducts(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiDataResponse.ok(productService.findInactiveProducts(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiDataResponse<ProductResponse>> findProductById(@PathVariable String id) {
        return ResponseEntity.ok(ApiDataResponse.ok(productService.findProductById(id)));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('PRODUCT_ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiDataResponse<ProductResponse>> updateProduct(
            @PathVariable String id,
            @RequestPart("product") @Valid CreateProductRequest request,
            MultipartHttpServletRequest httpRequest) {

        Map<Integer, List<MultipartFile>> newImages = extractVariantImages(httpRequest);
        return ResponseEntity.ok(ApiDataResponse.ok(productService.updateProduct(id, request, newImages)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PRODUCT_ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiDataResponse<Void>> deleteProduct(@PathVariable String id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiDataResponse.ok(null));
    }

    private Map<Integer, List<MultipartFile>> extractVariantImages(MultipartHttpServletRequest request) {
        Map<Integer, List<MultipartFile>> variantImages = new HashMap<>();
        for (String partName : request.getMultiFileMap().keySet()) {
            if (partName.startsWith("images_")) {
                try {
                    int index = Integer.parseInt(partName.substring("images_".length()));
                    variantImages.put(index, request.getFiles(partName));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "Tên part ảnh không hợp lệ: '" + partName
                            + "'. Định dạng đúng: images_0, images_1, ...");
                }
            }
        }
        return variantImages;
    }
}
