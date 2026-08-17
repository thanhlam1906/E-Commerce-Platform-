package com.voltstack.ecommerce.identity.controller;

import com.voltstack.ecommerce.identity.dto.request.UpdateRolesRequest;
import com.voltstack.ecommerce.identity.dto.request.UpdateStatusRequest;
import com.voltstack.ecommerce.identity.dto.request.UpdateUserRequest;
import com.voltstack.ecommerce.identity.dto.response.ApiDataResponse;
import com.voltstack.ecommerce.identity.dto.response.UserResponse;
import com.voltstack.ecommerce.identity.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiDataResponse<UserResponse>> me() {
        return ResponseEntity.ok(ApiDataResponse.ok(userService.me()));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiDataResponse<UserResponse>> updateMe(@Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(ApiDataResponse.ok(userService.updateMe(request)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PRODUCT_ADMIN', 'ORDER_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiDataResponse<Page<UserResponse>>> listUsers(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiDataResponse.ok(userService.listUsers(pageable)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiDataResponse<UserResponse>> updateStatus(
            @PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(ApiDataResponse.ok(userService.updateStatus(id, request)));
    }

    @PatchMapping("/{id}/roles")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiDataResponse<UserResponse>> updateRoles(
            @PathVariable UUID id, @Valid @RequestBody UpdateRolesRequest request) {
        return ResponseEntity.ok(ApiDataResponse.ok(userService.updateRoles(id, request)));
    }
}
