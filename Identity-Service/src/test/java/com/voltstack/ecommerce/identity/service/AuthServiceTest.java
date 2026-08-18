package com.voltstack.ecommerce.identity.service;

import com.voltstack.ecommerce.identity.dto.request.LoginRequest;
import com.voltstack.ecommerce.identity.dto.request.RefreshRequest;
import com.voltstack.ecommerce.identity.dto.request.RegisterRequest;
import com.voltstack.ecommerce.identity.dto.response.AuthResponse;
import com.voltstack.ecommerce.identity.exception.DuplicateResourceException;
import com.voltstack.ecommerce.identity.exception.InvalidCredentialsException;
import com.voltstack.ecommerce.identity.exception.TokenReuseException;
import com.voltstack.ecommerce.identity.model.RefreshToken;
import com.voltstack.ecommerce.identity.model.User;
import com.voltstack.ecommerce.identity.model.enums.Role;
import com.voltstack.ecommerce.identity.repository.RefreshTokenRepository;
import com.voltstack.ecommerce.identity.repository.UserRepository;
import com.voltstack.ecommerce.identity.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private User user() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("a@b.com")
                .passwordHash("hash")
                .fullName("A")
                .isActive(true)
                .build();
    }

    @Test
    void register_createsCustomerAndReturnsTokens() {
        when(userRepository.existsByEmail("a@b.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access");
        when(jwtService.generateRefreshToken()).thenReturn("raw-refresh");
        when(jwtService.hashToken("raw-refresh")).thenReturn("hash-refresh");

        RegisterRequest req = RegisterRequest.builder()
                .email("a@b.com").password("password123").fullName("A").build();

        AuthResponse res = authService.register(req);

        assertNotNull(res.getAccessToken());
        assertEquals("raw-refresh", res.getRefreshToken());
        assertEquals(Role.CUSTOMER, res.getUser().getRole());
        verify(passwordEncoder).encode("password123");
    }

    @Test
    void register_duplicateEmail_throws() {
        when(userRepository.existsByEmail("a@b.com")).thenReturn(true);

        RegisterRequest req = RegisterRequest.builder().email("a@b.com").password("password123").build();

        assertThrows(DuplicateResourceException.class, () -> authService.register(req));
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_wrongPassword_throws() {
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () ->
                authService.login(LoginRequest.builder().email("a@b.com").password("wrong").build()));
    }

    @Test
    void login_disabledAccount_throws() {
        User disabled = user();
        disabled.setActive(false);
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(disabled));
        when(passwordEncoder.matches("password123", "hash")).thenReturn(true);

        assertThrows(InvalidCredentialsException.class, () ->
                authService.login(LoginRequest.builder().email("a@b.com").password("password123").build()));
    }

    @Test
    void refresh_rotatesTokenInSameFamily() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        RefreshToken existing = RefreshToken.builder()
                .userId(userId)
                .tokenHash("hash-old")
                .familyId(familyId)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(refreshTokenRepository.findByTokenHash("hash-old")).thenReturn(Optional.of(existing));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user()));
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("new-access");
        when(jwtService.generateRefreshToken()).thenReturn("new-raw");
        when(jwtService.hashToken("new-raw")).thenReturn("new-hash");
        when(jwtService.hashToken("old-raw")).thenReturn("hash-old");

        AuthResponse res = authService.refresh(RefreshRequest.builder().refreshToken("old-raw").build());

        assertNotNull(res.getAccessToken());
        assertEquals("new-raw", res.getRefreshToken());
        assertEquals(familyId, existing.getFamilyId());
        assertNotNull(existing.getRevokedAt());
    }

    @Test
    void refresh_reusedToken_revokesFamily() {
        UUID familyId = UUID.randomUUID();
        RefreshToken reused = RefreshToken.builder()
                .userId(UUID.randomUUID())
                .tokenHash("hash-old")
                .familyId(familyId)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revokedAt(Instant.now())
                .build();
        RefreshToken sibling = RefreshToken.builder()
                .userId(UUID.randomUUID())
                .tokenHash("hash-sibling")
                .familyId(familyId)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(jwtService.hashToken("old-raw")).thenReturn("hash-old");
        when(refreshTokenRepository.findByTokenHash("hash-old")).thenReturn(Optional.of(reused));
        when(refreshTokenRepository.findByFamilyId(familyId)).thenReturn(List.of(reused, sibling));

        assertThrows(TokenReuseException.class, () ->
                authService.refresh(RefreshRequest.builder().refreshToken("old-raw").build()));

        // Mockito cannot verify transaction commit (the @Transactional(noRollbackFor=...)
        // behavior); that the family revocation is actually persisted is only provable
        // via a real-DB smoke test. Here we assert the sibling was revoked and saved
        // within the transaction.
        verify(refreshTokenRepository).save(sibling);
        assertNotNull(sibling.getRevokedAt());
    }

    @Test
    void login_unknownEmail_runsDummyMatchAndThrows() {
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () ->
                authService.login(LoginRequest.builder().email("a@b.com").password("password123").build()));

        // dummy bcrypt match keeps timing uniform with the wrong-password path
        verify(passwordEncoder).matches(eq("password123"), anyString());
    }
}
