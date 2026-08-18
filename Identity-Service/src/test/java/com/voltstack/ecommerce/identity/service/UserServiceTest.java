package com.voltstack.ecommerce.identity.service;

import com.voltstack.ecommerce.identity.controller.UserController;
import com.voltstack.ecommerce.identity.dto.request.UpdateRolesRequest;
import com.voltstack.ecommerce.identity.dto.request.UpdateStatusRequest;
import com.voltstack.ecommerce.identity.dto.request.UpdateUserRequest;
import com.voltstack.ecommerce.identity.dto.response.UserResponse;
import com.voltstack.ecommerce.identity.exception.ResourceNotFoundException;
import com.voltstack.ecommerce.identity.model.User;
import com.voltstack.ecommerce.identity.model.enums.Role;
import com.voltstack.ecommerce.identity.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private User user(UUID id) {
        return User.builder().id(id).email("a@b.com").fullName("A").isActive(true).build();
    }

    private void authenticate(UUID id) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(id.toString(), null, List.of()));
    }

    @Test
    void me_returnsCurrentUser() {
        UUID id = UUID.randomUUID();
        authenticate(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(user(id)));

        UserResponse res = userService.me();

        assertEquals(id, res.getId());
    }

    @Test
    void updateStatus_disablesUser() {
        UUID id = UUID.randomUUID();
        User u = user(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(u));

        UserResponse res = userService.updateStatus(id, UpdateStatusRequest.builder().active(false).build());

        assertEquals(false, res.isActive());
        assertEquals(false, u.isActive());
    }

    @Test
    void updateStatus_requiresSuperAdmin() throws NoSuchMethodException {
        PreAuthorize preAuthorize = UserController.class
                .getMethod("updateStatus", UUID.class, UpdateStatusRequest.class)
                .getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertTrue(preAuthorize.value().contains("SUPER_ADMIN"));
        assertFalse(preAuthorize.value().contains("PRODUCT_ADMIN"));
        assertFalse(preAuthorize.value().contains("ORDER_ADMIN"));
    }

    @Test
    void updateRoles_changesRole() {
        UUID id = UUID.randomUUID();
        User u = user(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(u));

        UserResponse res = userService.updateRoles(id, UpdateRolesRequest.builder().role(Role.SUPER_ADMIN).build());

        assertEquals(Role.SUPER_ADMIN, res.getRole());
    }

    @Test
    void me_missingUser_throwsNotFound() {
        UUID id = UUID.randomUUID();
        authenticate(id);
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.me());
    }

    @Test
    void updateMe_changesProfile() {
        UUID id = UUID.randomUUID();
        authenticate(id);
        User u = user(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(u));

        UserResponse res = userService.updateMe(
                UpdateUserRequest.builder().fullName("B").phone("0123").avatarUrl("url").build());

        assertEquals("B", res.getFullName());
        assertEquals("0123", res.getPhone());
    }
}
