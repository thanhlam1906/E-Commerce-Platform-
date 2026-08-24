package com.voltstack.ecommerce.payment.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

/**
 * Gateway injects X-User-Id / X-User-Roles (always overwrites, cannot be spoofed) plus X-Internal-Secret
 * (shared with the gateway via internal.service-token). This filter verifies that secret so identity headers
 * are only trusted on gateway-routed requests, then lifts them into the SecurityContext. Empty X-User-Id = guest.
 * Skipped on /internal/** (handled by {@link InternalTokenFilter}), /webhooks/** (public),
 * /actuator/** (health/monitoring) and the VNPay return URL (browser redirect).
 */
public class HeaderAuthFilter extends OncePerRequestFilter {

    private final String internalToken;

    public HeaderAuthFilter(@Value("${internal.service-token:}") String internalToken) {
        this.internalToken = internalToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path != null && (path.startsWith("/internal/")
                || path.startsWith("/webhooks/")
                || path.startsWith("/actuator/")
                || path.startsWith("/api/v1/payments/vnpay/return"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        SecurityContextHolder.clearContext();
        // Blank secret = not configured (dev). Keep legacy passthrough; security requires setting INTERNAL_SERVICE_TOKEN.
        if (internalToken != null && !internalToken.isBlank()) {
            String provided = request.getHeader("X-Internal-Secret");
            if (provided == null || !MessageDigest.isEqual(
                    internalToken.getBytes(StandardCharsets.UTF_8),
                    provided.getBytes(StandardCharsets.UTF_8))) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"code\":401,\"message\":\"Unauthorized\"}");
                return;
            }
        }
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
