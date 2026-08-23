package com.voltstack.ecommerce.payment.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityUtilsTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validUserId_returnsUuid() {
        UUID id = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(id.toString(), null, List.of()));

        assertEquals(id, SecurityUtils.currentUserId());
    }

    @Test
    void malformedUserId_throwsCleanBadRequest() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("not-a-uuid", null, List.of()));

        assertThrows(IllegalArgumentException.class, SecurityUtils::currentUserId);
    }

    @Test
    void noAuth_returnsNull() {
        SecurityContextHolder.clearContext();
        assertNull(SecurityUtils.currentUserId());
    }
}
