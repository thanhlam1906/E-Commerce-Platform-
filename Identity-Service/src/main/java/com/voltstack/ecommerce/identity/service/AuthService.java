package com.voltstack.ecommerce.identity.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltstack.ecommerce.identity.constant.ErrorMessages;
import com.voltstack.ecommerce.identity.dto.request.ForgotPasswordRequest;
import com.voltstack.ecommerce.identity.dto.request.LoginRequest;
import com.voltstack.ecommerce.identity.dto.request.RefreshRequest;
import com.voltstack.ecommerce.identity.dto.request.RegisterRequest;
import com.voltstack.ecommerce.identity.dto.request.ResetPasswordRequest;
import com.voltstack.ecommerce.identity.dto.request.VerifyEmailRequest;
import com.voltstack.ecommerce.identity.dto.response.AuthResponse;
import com.voltstack.ecommerce.identity.dto.response.UserResponse;
import com.voltstack.ecommerce.identity.exception.DuplicateResourceException;
import com.voltstack.ecommerce.identity.exception.InvalidCredentialsException;
import com.voltstack.ecommerce.identity.exception.ResourceNotFoundException;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);
    private static final Duration EMAIL_VERIFY_TTL = Duration.ofHours(24);
    private static final Duration PASSWORD_RESET_TTL = Duration.ofMinutes(30);

    // Dummy bcrypt hash (cost 12) used to equalize login timing when the email is unknown.
    private static final String DUMMY_PASSWORD_HASH = "$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${identity.kafka-topic}")
    private String topic;

    @Value("${identity.frontend-base-url}")
    private String frontendBaseUrl;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException(ErrorMessages.EMAIL_EXISTS);
        }
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .role(Role.CUSTOMER)
                .isActive(true)
                .build();
        userRepository.save(user);
        issueEmailVerification(user);
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> {
                    // equalize timing with the real bcrypt check below so unknown emails
                    // are not distinguishable by response time
                    passwordEncoder.matches(request.getPassword(), DUMMY_PASSWORD_HASH);
                    throw new InvalidCredentialsException(ErrorMessages.INVALID_CREDENTIALS);
                });
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException(ErrorMessages.INVALID_CREDENTIALS);
        }
        if (!user.isActive()) {
            throw new InvalidCredentialsException(ErrorMessages.INVALID_CREDENTIALS);
        }
        return buildAuthResponse(user);
    }

    /**
     * Google OAuth login (Cách A — backend code flow). User được xác thực bởi Google nên
     * email coi như đã verify. Email trùng tài khoản password → auto-link, cho vào thẳng.
     */
    @Transactional
    public AuthResponse loginWithGoogle(GoogleUserInfo info) {
        String email = info.email().trim().toLowerCase();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            user = User.builder()
                    .email(email)
                    // Google user không có mật khẩu → hash ngẫu nhiên, không dùng được để login
                    .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .fullName(info.fullName() == null || info.fullName().isBlank() ? email : info.fullName())
                    .avatarUrl(info.picture())
                    .role(Role.CUSTOMER)
                    .isActive(true)
                    .authProvider(AuthProvider.GOOGLE)
                    .providerId(info.sub())
                    .emailVerifiedAt(Instant.now())
                    .build();
            userRepository.save(user);
        } else {
            boolean changed = false;
            if (!AuthProvider.GOOGLE.equals(user.getAuthProvider()) || !info.sub().equals(user.getProviderId())) {
                user.setAuthProvider(AuthProvider.GOOGLE);
                user.setProviderId(info.sub());
                changed = true;
            }
            if (user.getEmailVerifiedAt() == null) {
                user.setEmailVerifiedAt(Instant.now());
                changed = true;
            }
            if ((user.getFullName() == null || user.getFullName().isBlank())
                    && info.fullName() != null && !info.fullName().isBlank()) {
                user.setFullName(info.fullName());
                changed = true;
            }
            if (info.picture() != null && !info.picture().equals(user.getAvatarUrl())) {
                user.setAvatarUrl(info.picture());
                changed = true;
            }
            if (changed) {
                userRepository.save(user);
            }
        }
        return buildAuthResponse(user);
    }

    @Transactional(noRollbackFor = TokenReuseException.class)
    public AuthResponse refresh(RefreshRequest request) {
        String hash = jwtService.hashToken(request.getRefreshToken());
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidCredentialsException(ErrorMessages.INVALID_REFRESH_TOKEN));

        if (existing.getRevokedAt() != null) {
            revokeFamily(existing.getFamilyId());
            throw new TokenReuseException(ErrorMessages.TOKEN_REUSE_DETECTED);
        }
        if (existing.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidCredentialsException(ErrorMessages.REFRESH_TOKEN_EXPIRED);
        }

        User user = userRepository.findById(existing.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.USER_NOT_FOUND));
        if (!user.isActive()) {
            throw new InvalidCredentialsException(ErrorMessages.INVALID_CREDENTIALS);
        }

        // rotate: revoke old token, issue new one in same family
        existing.setRevokedAt(Instant.now());
        refreshTokenRepository.save(existing);

        String accessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = issueRefreshToken(user.getId(), existing.getFamilyId());
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken)
                .user(UserResponse.from(user))
                .build();
    }

    @Transactional
    public void logout(RefreshRequest request) {
        String hash = jwtService.hashToken(request.getRefreshToken());
        refreshTokenRepository.findByTokenHash(hash)
                .filter(rt -> rt.getRevokedAt() == null)
                .ifPresent(rt -> revokeFamily(rt.getFamilyId()));
    }

    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        VerificationToken token = consumeToken(request.getToken(), VerificationPurpose.EMAIL_VERIFY,
                ErrorMessages.INVALID_VERIFICATION_TOKEN);
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.USER_NOT_FOUND));
        user.setEmailVerifiedAt(Instant.now());
        userRepository.save(user);
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            // luôn trả 200 kể cả email không tồn tại để chống enum tài khoản;
            // equalize timing with the real token generation/hash path below
            jwtService.hashToken(jwtService.generateRefreshToken());
            log.info("Forgot-password request for unknown email {}", email);
            return;
        }
        Instant expiresAt = Instant.now().plus(PASSWORD_RESET_TTL);
        String raw = jwtService.generateRefreshToken();
        saveToken(user.getId(), raw, VerificationPurpose.PASSWORD_RESET, expiresAt);
        try {
            publish("PasswordResetRequestedEvent", Map.of(
                    "userId", user.getId().toString(),
                    "email", user.getEmail(),
                    "resetLink", frontendBaseUrl + "/reset-password?token=" + raw,
                    "expiresAt", expiresAt.toString()));
        } catch (RuntimeException e) {
            // gửi sự kiện fail không được biến endpoint thành lỗi — user sẽ bấm gửi lại
            log.warn("Publish PasswordResetRequested failed for userId={}", user.getId(), e);
        }
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        VerificationToken token = consumeToken(request.getToken(), VerificationPurpose.PASSWORD_RESET,
                ErrorMessages.INVALID_RESET_TOKEN);
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.USER_NOT_FOUND));
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        // đổi mật khẩu → vô hiệu toàn bộ refresh token đang hoạt động của user
        refreshTokenRepository.findByUserIdAndRevokedAtIsNull(user.getId())
                .forEach(rt -> rt.setRevokedAt(Instant.now()));
    }

    private void issueEmailVerification(User user) {
        Instant expiresAt = Instant.now().plus(EMAIL_VERIFY_TTL);
        String raw = jwtService.generateRefreshToken();
        saveToken(user.getId(), raw, VerificationPurpose.EMAIL_VERIFY, expiresAt);
        publish("UserRegisteredEvent", Map.of(
                "userId", user.getId().toString(),
                "email", user.getEmail(),
                "verifyLink", frontendBaseUrl + "/verify-email?token=" + raw,
                "expiresAt", expiresAt.toString()));
    }

    /**
     * Lưu token một lần (chỉ lưu SHA-256 hash). Token cũ chưa dùng cùng mục đích bị vô hiệu
     * để mỗi mục đích chỉ có đúng một token hoạt động tại một thời điểm.
     */
    private void saveToken(UUID userId, String rawToken, VerificationPurpose purpose, Instant expiresAt) {
        Instant now = Instant.now();
        verificationTokenRepository.findByUserIdAndPurposeAndUsedAtIsNull(userId, purpose)
                .forEach(t -> t.setUsedAt(now));
        verificationTokenRepository.save(VerificationToken.builder()
                .userId(userId)
                .tokenHash(jwtService.hashToken(rawToken))
                .purpose(purpose)
                .expiresAt(expiresAt)
                .build());
    }

    private VerificationToken consumeToken(String rawToken, VerificationPurpose purpose, String errorMessage) {
        String hash = jwtService.hashToken(rawToken);
        // atomic single-use: the UPDATE claims the token only if it is unused, unexpired
        // and matches the purpose — concurrent re-use gets 0 rows and fails
        int updated = verificationTokenRepository.consume(hash, purpose, Instant.now());
        if (updated == 0) {
            throw new IllegalArgumentException(errorMessage);
        }
        return verificationTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new IllegalArgumentException(errorMessage));
    }

    private void publish(String eventType, Map<String, Object> data) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("eventType", eventType);
        envelope.put("data", data);
        try {
            String payload = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(topic, payload).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Không thể gửi sự kiện " + eventType, e);
        } catch (Exception e) {
            throw new IllegalStateException("Không thể gửi sự kiện " + eventType, e);
        }
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = issueRefreshToken(user.getId(), UUID.randomUUID());
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(UserResponse.from(user))
                .build();
    }

    private String issueRefreshToken(UUID userId, UUID familyId) {
        String raw = jwtService.generateRefreshToken();
        RefreshToken rt = RefreshToken.builder()
                .userId(userId)
                .tokenHash(jwtService.hashToken(raw))
                .familyId(familyId)
                .expiresAt(Instant.now().plus(REFRESH_TOKEN_TTL))
                .build();
        refreshTokenRepository.save(rt);
        return raw;
    }

    private void revokeFamily(UUID familyId) {
        refreshTokenRepository.findByFamilyId(familyId).forEach(rt -> {
            if (rt.getRevokedAt() == null) {
                rt.setRevokedAt(Instant.now());
                refreshTokenRepository.save(rt);
            }
        });
    }
}
