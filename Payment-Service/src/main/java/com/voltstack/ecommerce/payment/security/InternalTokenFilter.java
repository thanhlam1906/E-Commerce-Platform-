package com.voltstack.ecommerce.payment.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Authenticates Order→Payment internal calls via a shared bearer token
 * (internal.service-token, same value Order-Service sends). Acts on /internal/** and the
 * dev-only /webhooks/sandbox/** simulator (must be enabled explicitly).
 */
public class InternalTokenFilter extends OncePerRequestFilter {

    private final String internalToken;

    public InternalTokenFilter(@Value("${internal.service-token:}") String internalToken) {
        this.internalToken = internalToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path == null || !(path.startsWith("/internal/") || path.startsWith("/webhooks/sandbox/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean ok = internalToken != null && !internalToken.isBlank();
        if (ok) {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            byte[] expected = ("Bearer " + internalToken).getBytes(StandardCharsets.UTF_8);
            byte[] provided = header == null ? null : header.getBytes(StandardCharsets.UTF_8);
            ok = provided != null && MessageDigest.isEqual(expected, provided);
        }
        if (ok) {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("internal-service", null, List.of()));
        } else {
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }
}
