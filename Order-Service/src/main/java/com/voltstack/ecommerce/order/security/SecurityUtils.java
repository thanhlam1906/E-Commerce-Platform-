package com.voltstack.ecommerce.order.security;

import com.voltstack.ecommerce.order.constant.ErrorMessages;
import com.voltstack.ecommerce.order.exception.ResourceNotFoundException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {}

    /** Returns the gateway-injected user id, or null when the caller is a guest. */
    public static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // AnonymousAuthenticationToken: permitAll endpoint + no X-User-Id → guest (null), not "malformed".
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
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
            throw new ResourceNotFoundException(ErrorMessages.UNAUTHENTICATED);
        }
        return userId;
    }

    public static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream().anyMatch(a -> {
            String role = a.getAuthority();
            return "ROLE_ORDER_ADMIN".equals(role) || "ROLE_SUPER_ADMIN".equals(role);
        });
    }
}
