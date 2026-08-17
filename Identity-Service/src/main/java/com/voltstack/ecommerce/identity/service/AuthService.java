package com.voltstack.ecommerce.identity.service;

import com.voltstack.ecommerce.identity.constant.ErrorMessages;
import com.voltstack.ecommerce.identity.dto.request.LoginRequest;
import com.voltstack.ecommerce.identity.dto.request.RefreshRequest;
import com.voltstack.ecommerce.identity.dto.request.RegisterRequest;
import com.voltstack.ecommerce.identity.dto.response.AuthResponse;
import com.voltstack.ecommerce.identity.dto.response.UserResponse;
import com.voltstack.ecommerce.identity.exception.DuplicateResourceException;
import com.voltstack.ecommerce.identity.exception.InvalidCredentialsException;
import com.voltstack.ecommerce.identity.exception.ResourceNotFoundException;
import com.voltstack.ecommerce.identity.exception.TokenReuseException;
import com.voltstack.ecommerce.identity.model.RefreshToken;
import com.voltstack.ecommerce.identity.model.User;
import com.voltstack.ecommerce.identity.model.enums.Role;
import com.voltstack.ecommerce.identity.repository.RefreshTokenRepository;
import com.voltstack.ecommerce.identity.repository.UserRepository;
import com.voltstack.ecommerce.identity.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    // Dummy bcrypt hash (cost 12) used to equalize login timing when the email is unknown.
    private static final String DUMMY_PASSWORD_HASH = "$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

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
