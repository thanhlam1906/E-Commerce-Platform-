package com.example.productcatalogservice.service;

import com.example.productcatalogservice.constant.ErrorMessages;
import com.example.productcatalogservice.dto.request.CreateProductRequest;
import com.example.productcatalogservice.dto.response.ProductResponse;
import com.example.productcatalogservice.exception.DuplicateResourceException;
import com.example.productcatalogservice.exception.ResourceNotFoundException;
import com.example.productcatalogservice.mapper.ProductMapper;
import com.example.productcatalogservice.model.Category;
import com.example.productcatalogservice.model.Product;
import com.example.productcatalogservice.model.ProductVariant;
import com.example.productcatalogservice.model.enums.CategoryStatus;
import com.example.productcatalogservice.repository.CategoryRepository;
import com.example.productcatalogservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final CloudinaryService cloudinaryService;
    private final ProductMapper productMapper;
    private final MongoTemplate mongoTemplate;

    // ---- Create (multipart) ----

    public ProductResponse createProduct(CreateProductRequest request, Map<Integer, List<MultipartFile>> variantImages) {
        validateCategory(request.getCategoryId());
        validateSkuUnique(request,null);

        Product product = productMapper.toEntity(request);
        product.setSlug(toSlug(request.getName()));

        // Upload ảnh cho từng variant theo index
        for (var entry : variantImages.entrySet()) {
            int variantIndex = entry.getKey();
            if (variantIndex >= product.getVariants().size()) continue;
            List<String> urls = uploadImages(entry.getValue());
            product.getVariants().get(variantIndex).setImages(urls);
        }

        return productMapper.toResponse(saveProduct(product));
    }

    // ---- Read ----

    public Page<ProductResponse> findAllProducts(String categoryId, String brand, String keyword, Pageable pageable) {
        // Keyword (text search) thắng các filter khác — giữ hành vi cũ.
        if (keyword != null && !keyword.isBlank()) {
            return productRepository.searchByKeyword(keyword.trim(), pageable)
                    .map(productMapper::toResponse);
        }

        brand = (brand == null || brand.isBlank()) ? null : brand.trim();
        // categoryId là category cha → gom luôn sản phẩm của các category con (tree 2+ tầng).
        List<String> categoryIds = (categoryId == null || categoryId.isBlank())
                ? null
                : expandCategoryTree(categoryId);

        Page<Product> page;
        if (categoryIds != null && brand != null) {
            page = productRepository.findByCategoryIdInAndBrandAndIsActiveTrue(categoryIds, brand, pageable);
        } else if (categoryIds != null) {
            page = productRepository.findByCategoryIdInAndIsActiveTrue(categoryIds, pageable);
        } else if (brand != null) {
            page = productRepository.findByBrandAndIsActiveTrue(brand, pageable);
        } else {
            page = productRepository.findAllByIsActiveTrue(pageable);
        }
        return page.map(productMapper::toResponse);
    }

    /** Thương hiệu đang bán. categoryId tuỳ chọn → chỉ brand của sản phẩm thuộc category đó + các category con. */
    public List<String> findActiveBrands(String categoryId) {
        Query query = new Query(Criteria.where("isActive").is(true).and("brand").nin(null, ""));
        if (categoryId != null && !categoryId.isBlank()) {
            query.addCriteria(Criteria.where("categoryId").in(expandCategoryTree(categoryId)));
        }
        return mongoTemplate.findDistinct(query, "brand", Product.class, String.class).stream()
                .sorted()
                .toList();
    }

    /** BFS: bản thân category + toàn bộ category con (parentId). */
    private List<String> expandCategoryTree(String rootId) {
        List<Category> all = categoryRepository.findAllByStatus(CategoryStatus.ACTIVE, Pageable.unpaged()).getContent();
        Map<String, List<String>> childrenByParent = new HashMap<>();
        for (Category c : all) {
            childrenByParent.computeIfAbsent(c.getParentId() == null ? "" : c.getParentId(),
                    k -> new ArrayList<>()).add(c.getId());
        }

        List<String> result = new ArrayList<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            result.add(current);
            List<String> children = childrenByParent.get(current);
            if (children != null) queue.addAll(children);
        }
        return result;
    }

    public Page<ProductResponse> findInactiveProducts(Pageable pageable) {
        return productRepository.findAllByIsActiveFalse(pageable).map(productMapper::toResponse);
    }

    public ProductResponse findProductById(String id) {
        Product product = productRepository.findById(id)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.PRODUCT_NOT_FOUND + " với id: " + id));
        return productMapper.toResponse(product);
    }

    // ---- Update ----

    public ProductResponse updateProduct(String id, CreateProductRequest request,
                                         Map<Integer, List<MultipartFile>> newImages) {
        Product product = productRepository.findById(id)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.PRODUCT_NOT_FOUND + " với id: " + id));

        // Chụp toàn bộ ảnh cũ TRƯỚC khi merge (merge mutate trực tiếp list cũ)
        Set<String> oldImages = product.getVariants().stream()
                .filter(v -> v.getImages() != null)
                .flatMap(v -> v.getImages().stream())
                .collect(Collectors.toSet());

        validateCategory(request.getCategoryId());
        validateSkuUnique(request, product);

        product.setName(request.getName());
        product.setSlug(toSlug(request.getName()));
        product.setDescription(request.getDescription());
        product.setCategoryId(request.getCategoryId());
        product.setBrand(request.getBrand());

        // Merge variant theo SKU: trùng SKU → update, SKU mới → thêm mới, variant cũ không nằm trong request → giữ lại
        Map<String, ProductVariant> existingBySku = new HashMap<>();
        for (ProductVariant v : product.getVariants()) {
            existingBySku.put(v.getSku(), v);
        }

        List<ProductVariant> mergedVariants = new ArrayList<>(product.getVariants());
        for (int i = 0; i < request.getVariants().size(); i++) {
            var reqVar = request.getVariants().get(i);
            ProductVariant existing = existingBySku.get(reqVar.getSku());

            ProductVariant entity = existing != null ? existing : productMapper.toVariant(reqVar);
            if (existing != null) {
                entity.setName(reqVar.getName());
                entity.setPrice(reqVar.getPrice());
                entity.setSalePrice(reqVar.getSalePrice());
                entity.setSaleEndTime(reqVar.getSaleEndTime());
                entity.setAttributes(reqVar.getAttributes());
            }

            // Merge ảnh: giữ ảnh cũ (theo SKU) + upload ảnh mới
            List<String> finalImages = new ArrayList<>();
            if (reqVar.getImages() != null) {
                finalImages.addAll(reqVar.getImages());
            } else if (existing != null && existing.getImages() != null) {
                finalImages.addAll(existing.getImages());
            }

            List<MultipartFile> files = newImages.get(i);
            if (files != null && !files.isEmpty()) {
                finalImages.addAll(uploadImages(files));
            }

            entity.setImages(finalImages);
            if (existing == null) {
                mergedVariants.add(entity);
            }
        }
        product.setVariants(mergedVariants);
        product.setUpdatedAt(Instant.now());

        Product saved = saveProduct(product);

        // Xóa ảnh bị bỏ (best-effort) SAU khi save thành công — không throw ngược vào PUT
        Set<String> finalImages = saved.getVariants().stream()
                .filter(v -> v.getImages() != null)
                .flatMap(v -> v.getImages().stream())
                .collect(Collectors.toSet());
        oldImages.removeAll(finalImages);
        oldImages.forEach(cloudinaryService::deleteImage);

        return productMapper.toResponse(saved);
    }

    // ---- Delete (soft) ----

    public void deleteProduct(String id) {
        Product product = productRepository.findById(id)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.PRODUCT_NOT_FOUND + " với id: " + id));
        product.setActive(false);
        product.setUpdatedAt(Instant.now());
        productRepository.save(product);
    }

    // ---- Private helpers ----

    private Product saveProduct(Product product) {
        try {
            return productRepository.save(product);
        } catch (DuplicateKeyException e) {
            // Unique index trên slug hoặc variants.sku bị trùng
            throw new DuplicateResourceException(ErrorMessages.PRODUCT_SLUG_EXISTS);
        }
    }

    private void validateCategory(String categoryId) {
        if (categoryId == null) return;
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException(ErrorMessages.CATEGORY_NOT_FOUND + " với id: " + categoryId);
        }
    }

    private void validateSkuUnique(CreateProductRequest request, Product existing) {

        List<String> skus = request.getVariants().stream()
                .map(CreateProductRequest.Variant::getSku)
                .toList();
        List<Product> existingProducts = productRepository.findByVariantsSkuIn(skus);

        for (var v : request.getVariants()) {
            boolean skuExists = existingProducts.stream()
                    .anyMatch(p -> p.getVariants().stream()
                            .anyMatch(pv -> pv.getSku().equals(v.getSku())));

            if (!skuExists) continue;

            // Khi update: cho phép SKU của chính product đó
            boolean belongsToCurrent = existing != null
                    && existing.getVariants().stream()
                    .anyMatch(ev -> ev.getSku().equals(v.getSku()));
            if (!belongsToCurrent) {
                throw new DuplicateResourceException(ErrorMessages.SKU_EXISTS + ": " + v.getSku());
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
        if (name == null || name.isBlank()) {
            return "product";
        }
        String slug = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        slug = slug.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        return slug.isBlank() ? "product-" + Math.abs(name.hashCode()) : slug;
    }
}
