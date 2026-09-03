package com.voltstack.ecommerce.identity.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleAuthServiceTest {

    private final GoogleAuthService service =
            new GoogleAuthService("client-id.apps.googleusercontent.com", "secret",
                    "http://localhost:8080/api/v1/auth/google/callback", RestClient.builder());

    @Test
    void authorizationUrl_buildsGoogleUrlWithEncodedParams() {
        String url = service.authorizationUrl("state-123");

        assertTrue(url.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"));
        assertTrue(url.contains("client_id=client-id.apps.googleusercontent.com"));
        assertTrue(url.contains("redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapi%2Fv1%2Fauth%2Fgoogle%2Fcallback"));
        assertTrue(url.contains("response_type=code"));
        assertTrue(url.contains("scope="));
        assertTrue(url.contains("state=state-123"));
    }
}
