package com.voltstack.ecommerce.payment.security;

import com.voltstack.ecommerce.payment.exception.ResourceNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {}

    /** Returns the gateway-injected user id, or null when the caller is a guest. */
    public static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException | NullPointerException e) {
            // Malformed X-User-Id (or an auth whose name is not a UUID) → clean 400, never a 500.
            throw new IllegalArgumentException("X-User-Id không hợp lệ");
        }
    }

    /** Like {@link #currentUserId()} but fails fast for endpoints that require login. */
    public static UUID requireUserId() {
        UUID userId = currentUserId();
        if (userId == null) {
            throw new ResourceNotFoundException("Chưa xác thực");
        }
        return userId;
    }
}
