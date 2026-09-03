package com.example.productcatalogservice.service;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private CloudinaryService cloudinaryService;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private MongoTemplate mongoTemplate;
    @InjectMocks
    private ProductService productService;

    private ProductVariant pv(String sku, List<String> images) {
        return ProductVariant.builder()
                .sku(sku)
                .name("variant-" + sku)
                .price(BigDecimal.TEN)
                .images(images)
                .build();
    }

    private CreateProductRequest.Variant reqVariant(String sku) {
        CreateProductRequest.Variant v = new CreateProductRequest.Variant();
        v.setSku(sku);
        v.setName("variant-" + sku);
        v.setPrice(BigDecimal.TEN);
        return v;
    }

    // ---- findAllProducts ----

    private Category category(String id, String parentId) {
        return Category.builder().id(id).parentId(parentId).status(CategoryStatus.ACTIVE).build();
    }

    private void stubActiveCategories(Category... cats) {
        when(categoryRepository.findAllByStatus(eq(CategoryStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cats)));
    }

    @Test
    void findAllProducts_keyword_usesSearch() {
        Pageable pageable = Pageable.unpaged();
        when(productRepository.searchByKeyword("phone", pageable)).thenReturn(Page.empty());

        productService.findAllProducts(null, null, " phone ", pageable);

        verify(productRepository).searchByKeyword("phone", pageable);
        verify(productRepository, never()).findByCategoryIdInAndIsActiveTrue(any(), any());
    }

    @Test
    void findAllProducts_leafCategory_filtersExact() {
        Pageable pageable = Pageable.unpaged();
        stubActiveCategories(category("c1", null));
        when(productRepository.findByCategoryIdInAndIsActiveTrue(List.of("c1"), pageable)).thenReturn(Page.empty());

        productService.findAllProducts("c1", null, null, pageable);

        verify(productRepository).findByCategoryIdInAndIsActiveTrue(List.of("c1"), pageable);
    }

    @Test
    void findAllProducts_parentCategory_includesDescendants() {
        Pageable pageable = Pageable.unpaged();
        stubActiveCategories(category("c1", null), category("c1a", "c1"), category("c1b", "c1a"));
        when(productRepository.findByCategoryIdInAndIsActiveTrue(List.of("c1", "c1a", "c1b"), pageable))
                .thenReturn(Page.empty());

        productService.findAllProducts("c1", null, null, pageable);

        verify(productRepository).findByCategoryIdInAndIsActiveTrue(List.of("c1", "c1a", "c1b"), pageable);
    }

    @Test
    void findAllProducts_brand_filtersBrand() {
        Pageable pageable = Pageable.unpaged();
        when(productRepository.findByBrandAndIsActiveTrue("Nike", pageable)).thenReturn(Page.empty());

        productService.findAllProducts(null, " Nike ", null, pageable);

        verify(productRepository).findByBrandAndIsActiveTrue("Nike", pageable);
    }

    @Test
    void findAllProducts_categoryAndBrand_combines() {
        Pageable pageable = Pageable.unpaged();
        stubActiveCategories(category("c1", null));
        when(productRepository.findByCategoryIdInAndBrandAndIsActiveTrue(List.of("c1"), "Apple", pageable))
                .thenReturn(Page.empty());

        productService.findAllProducts("c1", "Apple", null, pageable);

        verify(productRepository).findByCategoryIdInAndBrandAndIsActiveTrue(List.of("c1"), "Apple", pageable);
    }

    @Test
    void findAllProducts_noFilter_usesAllActive() {
        Pageable pageable = Pageable.unpaged();
        when(productRepository.findAllByIsActiveTrue(pageable)).thenReturn(Page.empty());

        productService.findAllProducts(null, null, null, pageable);

        verify(productRepository).findAllByIsActiveTrue(pageable);
    }

    // ---- findActiveBrands ----

    @Test
    void findActiveBrands_all_returnsSortedDistinctNoCategoryFilter() {
        when(mongoTemplate.findDistinct(any(Query.class), eq("brand"), eq(Product.class), eq(String.class)))
                .thenReturn(List.of("Zara", "Apple"));

        assertEquals(List.of("Apple", "Zara"), productService.findActiveBrands(null));

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findDistinct(captor.capture(), eq("brand"), eq(Product.class), eq(String.class));
        assertFalse(captor.getValue().getQueryObject().containsKey("categoryId"));
    }

    @Test
    void findActiveBrands_byCategory_includesDescendantsInQuery() {
        stubActiveCategories(category("c1", null), category("c1a", "c1"));
        when(mongoTemplate.findDistinct(any(Query.class), eq("brand"), eq(Product.class), eq(String.class)))
                .thenReturn(List.of("Nike"));

        assertEquals(List.of("Nike"), productService.findActiveBrands("c1"));

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findDistinct(captor.capture(), eq("brand"), eq(Product.class), eq(String.class));
        String query = captor.getValue().toString();
        assertTrue(query.contains("categoryId"));
        assertTrue(query.contains("c1"));
        assertTrue(query.contains("c1a"));
    }

    // ---- findProductById ----

    @Test
    void findProductById_inactive_throws() {
        when(productRepository.findById("1")).thenReturn(Optional.of(Product.builder().id("1").isActive(false).build()));

        assertThrows(ResourceNotFoundException.class, () -> productService.findProductById("1"));
    }

    // ---- deleteProduct ----

    @Test
    void deleteProduct_setsInactive() {
        Product p = Product.builder().id("1").isActive(true).variants(List.of()).build();
        when(productRepository.findById("1")).thenReturn(Optional.of(p));

        productService.deleteProduct("1");

        assertFalse(p.isActive());
        verify(productRepository).save(p);
    }

    // ---- createProduct ----

    @Test
    void createProduct_generatesSlugFromName() {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Áo Thun");
        req.setVariants(List.of(reqVariant("SKU1")));

        Product entity = Product.builder().variants(List.of(pv("SKU1", List.of()))).build();
        when(productMapper.toEntity(req)).thenReturn(entity);
        when(productRepository.findByVariantsSkuIn(List.of("SKU1"))).thenReturn(List.of());
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productMapper.toResponse(any(Product.class))).thenReturn(mock(ProductResponse.class));

        productService.createProduct(req, Map.of());

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertEquals("ao-thun", captor.getValue().getSlug());
    }

    @Test
    void createProduct_duplicateSku_throws() {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("X");
        req.setVariants(List.of(reqVariant("SKU1")));

        Product other = Product.builder().variants(List.of(pv("SKU1", List.of()))).build();
        when(productRepository.findByVariantsSkuIn(List.of("SKU1"))).thenReturn(List.of(other));

        assertThrows(DuplicateResourceException.class, () -> productService.createProduct(req, Map.of()));
        verify(productRepository, never()).save(any());
    }

    // ---- updateProduct ----

    @Test
    void updateProduct_notFound_throws() {
        when(productRepository.findById("1")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> productService.updateProduct("1", new CreateProductRequest(), Map.of()));
    }

    @Test
    void updateProduct_foreignSku_throws() {
        Product existing = Product.builder().id("1").isActive(true)
                .variants(List.of(pv("SKU_OLD", List.of()))).build();
        when(productRepository.findById("1")).thenReturn(Optional.of(existing));

        Product other = Product.builder().id("2").variants(List.of(pv("SKU_NEW", List.of()))).build();
        when(productRepository.findByVariantsSkuIn(List.of("SKU_NEW"))).thenReturn(List.of(other));

        CreateProductRequest req = new CreateProductRequest();
        req.setName("New");
        req.setVariants(List.of(reqVariant("SKU_NEW")));

        assertThrows(DuplicateResourceException.class, () -> productService.updateProduct("1", req, Map.of()));
    }

    @Test
    void updateProduct_removedImage_deleted() {
        Product existing = Product.builder().id("1").isActive(true)
                .variants(List.of(pv("SKU1", List.of("img-old")))).build();
        when(productRepository.findById("1")).thenReturn(Optional.of(existing));
        when(productRepository.findByVariantsSkuIn(List.of("SKU1"))).thenReturn(List.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productMapper.toResponse(any(Product.class))).thenReturn(mock(ProductResponse.class));

        CreateProductRequest req = new CreateProductRequest();
        req.setName("New");
        req.setVariants(List.of(reqVariant("SKU1")));
        req.getVariants().get(0).setImages(List.of("img-new"));

        productService.updateProduct("1", req, Map.of());

        verify(cloudinaryService).deleteImage("img-old");
        verify(cloudinaryService, never()).deleteImage("img-new");
    }
}
