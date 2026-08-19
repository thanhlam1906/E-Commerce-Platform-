package com.voltstack.ecommerce.order.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class HeaderAuthFilterTest {

    private final HeaderAuthFilter filter = new HeaderAuthFilter();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void setsAuthenticationWithRolesFromHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "user-123");
        request.addHeader("X-User-Roles", " ORDER_ADMIN, SUPER_ADMIN ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("user-123", auth.getName());
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ORDER_ADMIN")));
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN")));
        verify(chain).doFilter(request, response);
    }

    @Test
    void blankUserId_leavesNoAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    void clearsContextBeforeEachRequest() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("prev", null, List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
