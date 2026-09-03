package com.voltstack.ecommerce.identity.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltstack.ecommerce.identity.dto.request.ForgotPasswordRequest;
import com.voltstack.ecommerce.identity.dto.request.LoginRequest;
import com.voltstack.ecommerce.identity.dto.request.RefreshRequest;
import com.voltstack.ecommerce.identity.dto.request.RegisterRequest;
import com.voltstack.ecommerce.identity.dto.request.ResetPasswordRequest;
import com.voltstack.ecommerce.identity.dto.request.VerifyEmailRequest;
import com.voltstack.ecommerce.identity.dto.response.AuthResponse;
import com.voltstack.ecommerce.identity.exception.DuplicateResourceException;
import com.voltstack.ecommerce.identity.exception.InvalidCredentialsException;
import com.voltstack.ecommerce.identity.exception.TokenReuseException;
import com.voltstack.ecommerce.identity.model.RefreshToken;
import com.voltstack.ecommerce.identity.model.User;
import com.voltstack.ecommerce.identity.model.VerificationToken;
import com.voltstack.ecommerce.identity.model.enums.AuthProvider;
import com.voltstack.ecommerce.identity.model.enums.Role;
import com.voltstack.ecommerce.identity.model.enums.VerificationPurpose;
import com.voltstack.ecommerce.identity.repository.RefreshTokenRepository;
import com.voltstack.ecommerce.identity.repository.UserRepository;
import com.voltstack.ecommerce.identity.repository.VerificationTokenRepository;
import com.voltstack.ecommerce.identity.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
    private VerificationTokenRepository verificationTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void stubKafkaSend() {
        lenient().when(kafkaTemplate.send(any(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        ReflectionTestUtils.setField(authService, "frontendBaseUrl", "http://localhost:3000");
    }

    private User user() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("a@b.com")
                .passwordHash("hash")
                .fullName("A")
                .isActive(true)
                .build();
    }

    private VerificationToken emailVerifyToken(UUID userId, Instant expiresAt) {
        return VerificationToken.builder()
                .userId(userId)
                .tokenHash("hash")
                .purpose(VerificationPurpose.EMAIL_VERIFY)
                .expiresAt(expiresAt)
                .build();
    }

    @Test
    void register_createsCustomerAndReturnsTokens() throws Exception {
        when(userRepository.existsByEmail("a@b.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            // JPA GenerationType.UUID gán id ngay khi save; mock cần mô phỏng hành vi này
            User u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId(UUID.randomUUID());
            }
            return u;
        });
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access");
        when(jwtService.generateRefreshToken()).thenReturn("raw-refresh");
        when(jwtService.hashToken("raw-refresh")).thenReturn("hash-refresh");
        when(verificationTokenRepository.findByUserIdAndPurposeAndUsedAtIsNull(any(), any())).thenReturn(List.of());

        RegisterRequest req = RegisterRequest.builder()
                .email("a@b.com").password("password123").fullName("A").build();

        AuthResponse res = authService.register(req);

        assertNotNull(res.getAccessToken());
        assertEquals("raw-refresh", res.getRefreshToken());
        assertEquals(Role.CUSTOMER, res.getUser().getRole());
        verify(passwordEncoder).encode("password123");

        // register tạo email-verify token + publish UserRegisteredEvent
        ArgumentCaptor<VerificationToken> tokenCaptor = ArgumentCaptor.forClass(VerificationToken.class);
        verify(verificationTokenRepository).save(tokenCaptor.capture());
        assertEquals(VerificationPurpose.EMAIL_VERIFY, tokenCaptor.getValue().getPurpose());

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(any(), payloadCaptor.capture());
        JsonNode node = objectMapper.readTree(payloadCaptor.getValue());
        assertEquals("UserRegisteredEvent", node.get("eventType").asText());
        assertEquals("a@b.com", node.get("data").get("email").asText());
        assertEquals("http://localhost:3000/verify-email?token=raw-refresh",
                node.get("data").get("verifyLink").asText());
        assertTrue(node.get("eventId").asText().length() > 0);
    }

    @Test
    void register_duplicateEmail_throws() {
        when(userRepository.existsByEmail("a@b.com")).thenReturn(true);

        RegisterRequest req = RegisterRequest.builder().email("a@b.com").password("password123").build();

        assertThrows(DuplicateResourceException.class, () -> authService.register(req));
        verify(userRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), anyString());
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

    @Test
    void verifyEmail_success_marksUserVerifiedAndConsumesToken() {
        User u = user();
        VerificationToken token = emailVerifyToken(u.getId(), Instant.now().plusSeconds(3600));
        when(jwtService.hashToken("raw")).thenReturn("hash");
        when(verificationTokenRepository.consume(eq("hash"), eq(VerificationPurpose.EMAIL_VERIFY), any(Instant.class)))
                .thenReturn(1);
        when(verificationTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(token));
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));

        authService.verifyEmail(VerifyEmailRequest.builder().token("raw").build());

        assertNotNull(u.getEmailVerifiedAt());
        verify(verificationTokenRepository).consume(eq("hash"), eq(VerificationPurpose.EMAIL_VERIFY), any(Instant.class));
        verify(verificationTokenRepository, never()).save(any());
        verify(userRepository).save(u);
    }

    @Test
    void verifyEmail_unknownToken_throwsBadRequest() {
        when(jwtService.hashToken("bad")).thenReturn("bad-hash");
        when(verificationTokenRepository.consume(eq("bad-hash"), eq(VerificationPurpose.EMAIL_VERIFY), any(Instant.class)))
                .thenReturn(0);

        assertThrows(IllegalArgumentException.class, () ->
                authService.verifyEmail(VerifyEmailRequest.builder().token("bad").build()));
        verify(userRepository, never()).save(any());
        verify(verificationTokenRepository, never()).save(any());
    }

    @Test
    void forgotPassword_unknownEmail_generatesThrowawayTokenWithoutPublishing() {
        when(userRepository.findByEmail("nobody@x.com")).thenReturn(Optional.empty());

        authService.forgotPassword(ForgotPasswordRequest.builder().email("nobody@x.com").build());

        // dummy generate+hash keeps timing uniform with the known-email path
        verify(jwtService).generateRefreshToken();
        verify(jwtService).hashToken(any());
        verify(verificationTokenRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), anyString());
    }

    @Test
    void forgotPassword_existingEmail_createsResetTokenAndPublishes() throws Exception {
        User u = user();
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(u));
        when(jwtService.generateRefreshToken()).thenReturn("raw-reset");
        when(jwtService.hashToken("raw-reset")).thenReturn("hash-reset");
        when(verificationTokenRepository.findByUserIdAndPurposeAndUsedAtIsNull(any(), any())).thenReturn(List.of());

        authService.forgotPassword(ForgotPasswordRequest.builder().email("a@b.com").build());

        ArgumentCaptor<VerificationToken> tokenCaptor = ArgumentCaptor.forClass(VerificationToken.class);
        verify(verificationTokenRepository).save(tokenCaptor.capture());
        assertEquals(VerificationPurpose.PASSWORD_RESET, tokenCaptor.getValue().getPurpose());
        assertEquals("hash-reset", tokenCaptor.getValue().getTokenHash());

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(any(), payloadCaptor.capture());
        JsonNode node = objectMapper.readTree(payloadCaptor.getValue());
        assertEquals("PasswordResetRequestedEvent", node.get("eventType").asText());
        assertEquals(u.getId().toString(), node.get("data").get("userId").asText());
        assertEquals("a@b.com", node.get("data").get("email").asText());
        assertEquals("http://localhost:3000/reset-password?token=raw-reset",
                node.get("data").get("resetLink").asText());
        assertTrue(node.get("data").get("expiresAt").asText().endsWith("Z"));
    }

    @Test
    void forgotPassword_existingEmail_publishFailureStillSucceeds() {
        User u = user();
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(u));
        when(jwtService.generateRefreshToken()).thenReturn("raw-reset");
        when(jwtService.hashToken("raw-reset")).thenReturn("hash-reset");
        when(verificationTokenRepository.findByUserIdAndPurposeAndUsedAtIsNull(any(), any())).thenReturn(List.of());
        lenient().when(kafkaTemplate.send(any(), anyString()))
                .thenThrow(new IllegalStateException("broker down"));

        // không ném ra ngoài — endpoint vẫn trả 200
        authService.forgotPassword(ForgotPasswordRequest.builder().email("a@b.com").build());
    }

    @Test
    void resetPassword_success_changesPasswordAndRevokesActiveRefreshTokens() {
        User u = user();
        VerificationToken token = VerificationToken.builder()
                .userId(u.getId())
                .tokenHash("hash")
                .purpose(VerificationPurpose.PASSWORD_RESET)
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();
        RefreshToken active = RefreshToken.builder().userId(u.getId()).revokedAt(null).build();
        when(jwtService.hashToken("raw")).thenReturn("hash");
        when(verificationTokenRepository.consume(eq("hash"), eq(VerificationPurpose.PASSWORD_RESET), any(Instant.class)))
                .thenReturn(1);
        when(verificationTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(token));
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        when(passwordEncoder.encode("newPass123")).thenReturn("new-hash");
        when(refreshTokenRepository.findByUserIdAndRevokedAtIsNull(u.getId())).thenReturn(List.of(active));

        authService.resetPassword(ResetPasswordRequest.builder().token("raw").newPassword("newPass123").build());

        assertEquals("new-hash", u.getPasswordHash());
        assertNotNull(active.getRevokedAt());
        verify(userRepository).save(u);
        verify(verificationTokenRepository).consume(eq("hash"), eq(VerificationPurpose.PASSWORD_RESET), any(Instant.class));
        verify(verificationTokenRepository, never()).save(any());
    }

    @Test
    void resetPassword_invalidToken_throwsBadRequest() {
        when(jwtService.hashToken("bad")).thenReturn("bad-hash");
        when(verificationTokenRepository.consume(eq("bad-hash"), eq(VerificationPurpose.PASSWORD_RESET), any(Instant.class)))
                .thenReturn(0);

        assertThrows(IllegalArgumentException.class, () ->
                authService.resetPassword(ResetPasswordRequest.builder().token("bad").newPassword("newPass123").build()));
        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void loginWithGoogle_newUser_createsGoogleUserAndReturnsTokens() throws Exception {
        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId(UUID.randomUUID());
            }
            return u;
        });
        when(passwordEncoder.encode(anyString())).thenReturn("random-hash");
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access");
        when(jwtService.generateRefreshToken()).thenReturn("raw-refresh");
        when(jwtService.hashToken("raw-refresh")).thenReturn("hash-refresh");

        AuthResponse res = authService.loginWithGoogle(
                new GoogleUserInfo("sub-1", "User@GMAIL.com", "User Name", "http://pic"));

        assertNotNull(res.getAccessToken());
        assertEquals("raw-refresh", res.getRefreshToken());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertEquals("user@gmail.com", saved.getEmail());
        assertEquals(AuthProvider.GOOGLE, saved.getAuthProvider());
        assertEquals("sub-1", saved.getProviderId());
        assertEquals("User Name", saved.getFullName());
        assertEquals("http://pic", saved.getAvatarUrl());
        assertNotNull(saved.getEmailVerifiedAt());
        // password hash là giá trị ngẫu nhiên (không phải mật khẩu user nhập), không dùng để login được
        assertEquals("random-hash", saved.getPasswordHash());
    }

    @Test
    void loginWithGoogle_existingEmail_linksProviderAndVerifiesEmail() {
        User u = user();
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(u));
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access");
        when(jwtService.generateRefreshToken()).thenReturn("raw-refresh");
        when(jwtService.hashToken("raw-refresh")).thenReturn("hash-refresh");

        authService.loginWithGoogle(new GoogleUserInfo("sub-9", "a@b.com", "A", null));

        verify(userRepository).save(u);
        assertEquals(AuthProvider.GOOGLE, u.getAuthProvider());
        assertEquals("sub-9", u.getProviderId());
        assertNotNull(u.getEmailVerifiedAt());
    }

    @Test
    void loginWithGoogle_existingGoogleUser_noDuplicateSave() {
        User u = user();
        u.setAuthProvider(AuthProvider.GOOGLE);
        u.setProviderId("sub-1");
        u.setEmailVerifiedAt(Instant.now());
        u.setFullName("A");
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(u));
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access");
        when(jwtService.generateRefreshToken()).thenReturn("raw-refresh");
        when(jwtService.hashToken("raw-refresh")).thenReturn("hash-refresh");

        authService.loginWithGoogle(new GoogleUserInfo("sub-1", "a@b.com", "A", null));

        verify(userRepository, never()).save(any());
        assertNotNull(u.getEmailVerifiedAt());
    }
}
