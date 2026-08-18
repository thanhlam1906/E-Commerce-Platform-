package com.example.productcatalogservice.controller;

import com.example.productcatalogservice.config.SecurityConfig;
import com.example.productcatalogservice.service.CategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryController.class)
@Import(SecurityConfig.class)
class CategoryControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @Test
    void getCategories_noToken_returns200() throws Exception {
        when(categoryService.findAllCategories(any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk());
    }

    @Test
    void getInactiveCategories_noToken_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/categories/inactive"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void getInactiveCategories_customerRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/categories/inactive").header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getInactiveCategories_adminRole_routesToInactiveService() throws Exception {
        when(categoryService.findInactiveCategories(any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/categories/inactive").header("X-User-Roles", "PRODUCT_ADMIN"))
                .andExpect(status().isOk());

        verify(categoryService).findInactiveCategories(any());
        verify(categoryService, never()).findCategoryById(any());
    }
}
