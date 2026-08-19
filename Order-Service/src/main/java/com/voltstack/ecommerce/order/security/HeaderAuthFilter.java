package com.voltstack.ecommerce.order.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Gateway injects X-User-Id / X-User-Roles (always overwrites, cannot be spoofed).
 * This filter just lifts them into the SecurityContext. Empty X-User-Id = guest.
 */
@Component
public class HeaderAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        SecurityContextHolder.clearContext();
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            String rolesHeader = request.getHeader("X-User-Roles");
            String[] roleParts = (rolesHeader == null || rolesHeader.isBlank()) ? new String[0] : rolesHeader.split(",");
            List<SimpleGrantedAuthority> authorities = Arrays.stream(roleParts)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> new SimpleGrantedAuthority("ROLE_" + s.toUpperCase()))
                    .toList();
            var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(request, response);
    }
}
