package com.voltstack.ecommerce.identity.service;

import com.voltstack.ecommerce.identity.constant.ErrorMessages;
import com.voltstack.ecommerce.identity.dto.request.UpdateRolesRequest;
import com.voltstack.ecommerce.identity.dto.request.UpdateStatusRequest;
import com.voltstack.ecommerce.identity.dto.request.UpdateUserRequest;
import com.voltstack.ecommerce.identity.dto.response.UserResponse;
import com.voltstack.ecommerce.identity.exception.ResourceNotFoundException;
import com.voltstack.ecommerce.identity.model.User;
import com.voltstack.ecommerce.identity.repository.UserRepository;
import com.voltstack.ecommerce.identity.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserResponse me() {
        return UserResponse.from(findById(SecurityUtils.currentUserId()));
    }

    @Transactional
    public UserResponse updateMe(UpdateUserRequest request) {
        User user = findById(SecurityUtils.currentUserId());
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setAvatarUrl(request.getAvatarUrl());
        userRepository.save(user);
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserResponse::from);
    }

    @Transactional
    public UserResponse updateStatus(UUID id, UpdateStatusRequest request) {
        User user = findById(id);
        user.setActive(request.getActive());
        userRepository.save(user);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateRoles(UUID id, UpdateRolesRequest request) {
        User user = findById(id);
        user.setRole(request.getRole());
        userRepository.save(user);
        return UserResponse.from(user);
    }

    private User findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.USER_NOT_FOUND));
    }
}
