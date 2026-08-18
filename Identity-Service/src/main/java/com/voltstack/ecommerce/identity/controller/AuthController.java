package com.voltstack.ecommerce.identity.controller;

import com.voltstack.ecommerce.identity.dto.request.LoginRequest;
import com.voltstack.ecommerce.identity.dto.request.RefreshRequest;
import com.voltstack.ecommerce.identity.dto.request.RegisterRequest;
import com.voltstack.ecommerce.identity.dto.response.ApiDataResponse;
import com.voltstack.ecommerce.identity.dto.response.AuthResponse;
import com.voltstack.ecommerce.identity.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiDataResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiDataResponse.created(authService.register(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiDataResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiDataResponse.ok(authService.login(request)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiDataResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(ApiDataResponse.ok(authService.refresh(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiDataResponse<Void>> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiDataResponse.ok(null));
    }
}
