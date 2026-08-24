package com.voltstack.ecommerce.identity.controller;

import com.voltstack.ecommerce.identity.dto.request.ForgotPasswordRequest;
import com.voltstack.ecommerce.identity.dto.request.ResetPasswordRequest;
import com.voltstack.ecommerce.identity.dto.request.VerifyEmailRequest;
import com.voltstack.ecommerce.identity.dto.response.ApiDataResponse;
import com.voltstack.ecommerce.identity.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class UserVerificationController {

    private final AuthService authService;

    @GetMapping("/verify-email")
    public ResponseEntity<ApiDataResponse<Void>> verifyEmail(@Valid VerifyEmailRequest request) {
        authService.verifyEmail(request);
        return ResponseEntity.ok(ApiDataResponse.ok(null));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiDataResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiDataResponse.ok(null));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiDataResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiDataResponse.ok(null));
    }
}
