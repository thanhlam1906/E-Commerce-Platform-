package com.voltstack.ecommerce.identity.controller;

import com.voltstack.ecommerce.identity.security.JwtKeyProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class JwksController {

    private final JwtKeyProvider keyProvider;

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return Map.of("keys", List.of(keyProvider.getRsaKey().toPublicJWK().toJSONObject()));
    }
}
