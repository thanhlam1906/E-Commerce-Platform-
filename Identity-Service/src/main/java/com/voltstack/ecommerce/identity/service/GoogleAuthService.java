package com.voltstack.ecommerce.identity.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Google OAuth 2.0 Authorization Code flow (Cách A — backend xử lý toàn bộ).
 * Client-secret chỉ tồn tại trong backend, không bao giờ lộ ra frontend.
 */
@Slf4j
@Service
public class GoogleAuthService {

    private static final String AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final RestClient restClient;

    public GoogleAuthService(@Value("${google.oauth.client-id}") String clientId,
                             @Value("${google.oauth.client-secret}") String clientSecret,
                             @Value("${google.oauth.redirect-uri}") String redirectUri,
                             RestClient.Builder restClientBuilder) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.restClient = restClientBuilder.build();
    }

    /** URL đưa user sang Google; state chống CSRF (controller set cookie và verify ở callback). */
    public String authorizationUrl(String state) {
        return AUTHORIZE_URL
                + "?client_id=" + urlEncode(clientId)
                + "&redirect_uri=" + urlEncode(redirectUri)
                + "&response_type=code"
                + "&scope=" + urlEncode("openid email profile")
                + "&state=" + urlEncode(state);
    }

    /** Đổi code lấy token rồi lấy thông tin user (sub, email, name, picture) từ Google. */
    public GoogleUserInfo fetchUserInfo(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");

        TokenResponse token = restClient.post().uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(HttpStatusCode::isError,
                        (req, res) -> {
                            throw new IllegalStateException("Google token exchange failed: " + res.getStatusCode());
                        })
                .body(TokenResponse.class);

        UserInfo info = restClient.get().uri(USERINFO_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.accessToken())
                .retrieve()
                .onStatus(HttpStatusCode::isError,
                        (req, res) -> {
                            throw new IllegalStateException("Google userinfo failed: " + res.getStatusCode());
                        })
                .body(UserInfo.class);

        if (info.email() == null || info.email().isBlank() || !Boolean.TRUE.equals(info.emailVerified())) {
            throw new IllegalStateException("Google email missing or not verified");
        }
        return new GoogleUserInfo(info.sub(), info.email().trim().toLowerCase(), info.name(), info.picture());
    }

    private String urlEncode(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private record TokenResponse(@JsonProperty("access_token") String accessToken,
                                 @JsonProperty("id_token") String idToken) {
    }

    private record UserInfo(String sub, String email,
                            @JsonProperty("email_verified") Boolean emailVerified,
                            String name, String picture) {
    }
}
