package com.example.productcatalogservice.service;

import com.example.productcatalogservice.constant.ErrorMessages;
import com.example.productcatalogservice.dto.request.CreateProductRequest;
import com.example.productcatalogservice.dto.response.ProductResponse;
import com.example.productcatalogservice.exception.DuplicateResourceException;
import com.example.productcatalogservice.exception.ResourceNotFoundException;
import com.example.productcatalogservice.mapper.ProductMapper;
import com.example.productcatalogservice.model.Product;
import com.example.productcatalogservice.model.ProductVariant;
import com.example.productcatalogservice.repository.CategoryRepository;
import com.example.productcatalogservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final CloudinaryService cloudinaryService;
    private final ProductMapper productMapper;

    // ---- Create (multipart) ----

    public ProductResponse createProduct(CreateProductRequest request, Map<Integer, List<MultipartFile>> variantImages) {
        validateCategory(request.getCategoryId());
        validateSkuUnique(request);

        Product product = productMapper.toEntity(request);
        product.setSlug(toSlug(request.getName()));
        product.setCreatedAt(Instant.now());
        product.setUpdatedAt(Instant.now());

        // Upload ảnh cho từng variant theo index
        for (var entry : variantImages.entrySet()) {
            int variantIndex = entry.getKey();
            if (variantIndex >= product.getVariants().size()) continue;
            List<String> urls = uploadImages(entry.getValue());
            product.getVariants().get(variantIndex).setImages(urls);
        }

        return productMapper.toResponse(productRepository.save(product));
    }

    // ---- Read ----

    public Page<ProductResponse> findAllProducts(String categoryId, String keyword, Pageable pageable) {
        if (keyword != null && !keyword.isBlank()) {
            return productRepository.findByNameContainingIgnoreCaseAndIsActiveTrue(keyword, pageable)
                    .map(productMapper::toResponse);
        }
        if (categoryId != null && !categoryId.isBlank()) {
            return productRepository.findByCategoryIdAndIsActiveTrue(categoryId, pageable)
                    .map(productMapper::toResponse);
        }
        return productRepository.findAllByIsActiveTrue(pageable).map(productMapper::toResponse);
    }

    public ProductResponse findProductById(String id) {
        Product product = productRepository.findById(id)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.PRODUCT_NOT_FOUND + " với id: " + id));
        return productMapper.toResponse(product);
    }

    // ---- Update ----

    public ProductResponse updateProduct(String id, CreateProductRequest request) {
        Product product = productRepository.findById(id)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.PRODUCT_NOT_FOUND + " với id: " + id));

        validateCategory(request.getCategoryId());
        validateSkuUniqueForUpdate(request, product);

        product.setName(request.getName());
        product.setSlug(toSlug(request.getName()));
        product.setDescription(request.getDescription());
        product.setCategoryId(request.getCategoryId());
        product.setBrand(request.getBrand());
        List<ProductVariant> newVariants = new ArrayList<>();
        for (int i = 0; i < request.getVariants().size(); i++){
            var reqVar = request.getVariants().get(i);
            ProductVariant entity = productMapper.toVariant(reqVar);

            // Nếu request không gửi ảnh mới → giữ ảnh cũ (nếu có variant cũ cùng index)
            if ((reqVar.getImages() == null || reqVar.getImages().isEmpty())
                    && i < product.getVariants().size()) {
                entity.setImages(product.getVariants().get(i).getImages());
            }
            newVariants.add(entity);
        }
        product.setVariants(newVariants);
        product.setUpdatedAt(Instant.now());

        return productMapper.toResponse(productRepository.save(product));
    }

    // ---- Delete (soft) ----

    public void deleteProduct(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.PRODUCT_NOT_FOUND + " với id: " + id));
        product.setActive(false);
        product.setUpdatedAt(Instant.now());
        productRepository.save(product);
    }

    // ---- Private helpers ----

    private void validateCategory(String categoryId) {
        if (categoryId == null) return;
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException(ErrorMessages.CATEGORY_NOT_FOUND + " với id: " + categoryId);
        }
    }

    private void validateSkuUnique(CreateProductRequest request) {
        for (var v : request.getVariants()) {
            if (productRepository.existsByVariantsSku(v.getSku())) {
                throw new DuplicateResourceException(ErrorMessages.SKU_EXISTS + ": " + v.getSku());
            }
        }
    }

    private void validateSkuUniqueForUpdate(CreateProductRequest request, Product existing) {
        for (var v : request.getVariants()) {
            if (productRepository.existsByVariantsSku(v.getSku())) {
                boolean belongsToCurrent = existing.getVariants().stream()
                        .anyMatch(ev -> ev.getSku().equals(v.getSku()));
                if (!belongsToCurrent) {
                    throw new DuplicateResourceException(ErrorMessages.SKU_EXISTS + ": " + v.getSku());
                }
            }
        }
    }

    private List<String> uploadImages(List<MultipartFile> images) {
        if (images == null || images.isEmpty()) return List.of();
        List<String> urls = new ArrayList<>();
        for (MultipartFile file : images) {
            if (!file.isEmpty()) {
                urls.add(cloudinaryService.upload(file));
            }
        }
        return urls;
    }

    private String toSlug(String name) {
        String slug = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return slug.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
