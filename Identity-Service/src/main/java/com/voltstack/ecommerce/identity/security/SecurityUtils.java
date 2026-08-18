package com.voltstack.ecommerce.identity.security;

import com.voltstack.ecommerce.identity.constant.ErrorMessages;
import com.voltstack.ecommerce.identity.exception.InvalidCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new InvalidCredentialsException(ErrorMessages.UNAUTHENTICATED);
        }
        return UUID.fromString(auth.getName());
    }
}
