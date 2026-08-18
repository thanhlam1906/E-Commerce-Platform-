package com.example.productcatalogservice.controller;

import com.example.productcatalogservice.config.SecurityConfig;
import com.example.productcatalogservice.dto.response.ProductResponse;
import com.example.productcatalogservice.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
class ProductControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void getProducts_noToken_returns200() throws Exception {
        when(productService.findAllProducts(any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk());
    }

    @Test
    void postProduct_noToken_returns401() throws Exception {
        mockMvc.perform(multipart("/api/v1/products").file(productPart()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void postProduct_customerRole_returns403() throws Exception {
        mockMvc.perform(multipart("/api/v1/products")
                        .file(productPart())
                        .header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void postProduct_adminRole_returnsCreated() throws Exception {
        when(productService.createProduct(any(), any())).thenReturn(mock(ProductResponse.class));

        mockMvc.perform(multipart("/api/v1/products")
                        .file(productPart())
                        .header("X-User-Roles", "PRODUCT_ADMIN"))
                .andExpect(status().isCreated());
    }

    @Test
    void getInactiveProducts_noToken_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/products/inactive"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void getInactiveProducts_customerRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/products/inactive").header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getInactiveProducts_adminRole_routesToInactiveService() throws Exception {
        when(productService.findInactiveProducts(any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/products/inactive").header("X-User-Roles", "PRODUCT_ADMIN"))
                .andExpect(status().isOk());

        verify(productService).findInactiveProducts(any());
        verify(productService, never()).findProductById(any());
    }

    private MockMultipartFile productPart() {
        return new MockMultipartFile("product", "product.json", MediaType.APPLICATION_JSON_VALUE,
                "{\"name\":\"Ao Thun\",\"variants\":[{\"sku\":\"SKU1\",\"name\":\"Do\",\"price\":10}]}".getBytes());
    }
}
