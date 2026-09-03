package com.voltstack.ecommerce.identity.controller;

import com.voltstack.ecommerce.identity.dto.response.AuthResponse;
import com.voltstack.ecommerce.identity.service.AuthService;
import com.voltstack.ecommerce.identity.service.GoogleAuthService;
import com.voltstack.ecommerce.identity.service.GoogleUserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

/**
 * Google OAuth Authorization Code flow (Cách A). State dùng cookie HttpOnly chống CSRF:
 * {@code /auth/google} set cookie, callback verify khớp với param state rồi mới xử lý.
 * Sau khi login, redirect về frontend kèm access_token + refresh_token.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class OAuthController {

    private static final String STATE_COOKIE = "oauth_state";

    private final GoogleAuthService googleAuthService;
    private final AuthService authService;

    @Value("${identity.frontend-base-url}")
    private String frontendBaseUrl;

    @GetMapping("/google")
    public ResponseEntity<Void> google() {
        String state = UUID.randomUUID().toString();
        ResponseCookie cookie = ResponseCookie.from(STATE_COOKIE, state)
                .path("/api/v1/auth/google")
                .httpOnly(true)
                .secure(false) // ponytail: dev HTTP; bật true khi deploy sau TLS
                .sameSite("Lax")
                .maxAge(Duration.ofMinutes(10))
                .build();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, googleAuthService.authorizationUrl(state))
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    @GetMapping("/google/callback")
    public ResponseEntity<Void> callback(@RequestParam("code") String code,
                                         @RequestParam(value = "state", required = false) String state,
                                         @RequestParam(value = "error", required = false) String error,
                                         @CookieValue(value = STATE_COOKIE, required = false) String expectedState) {
        if (error != null) {
            return redirectFrontend("error=" + error);
        }
        if (expectedState == null || !expectedState.equals(state)) {
            log.warn("OAuth state mismatch, rejecting callback");
            return redirectFrontend("error=invalid_state");
        }
        try {
            GoogleUserInfo info = googleAuthService.fetchUserInfo(code);
            AuthResponse res = authService.loginWithGoogle(info);
            return redirectFrontend("access_token=" + res.getAccessToken()
                    + "&refresh_token=" + res.getRefreshToken());
        } catch (RuntimeException e) {
            log.warn("Google callback failed", e);
            return redirectFrontend("error=oauth_failed");
        }
    }

    private ResponseEntity<Void> redirectFrontend(String query) {
        ResponseCookie clear = ResponseCookie.from(STATE_COOKIE, "")
                .path("/api/v1/auth/google")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .build();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, frontendBaseUrl + "/auth/callback?" + query)
                .header(HttpHeaders.SET_COOKIE, clear.toString())
                .build();
    }
}
