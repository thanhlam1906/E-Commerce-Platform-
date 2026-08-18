package com.voltstack.ecommerce.identity.service;

import com.voltstack.ecommerce.identity.constant.ErrorMessages;
import com.voltstack.ecommerce.identity.dto.request.CreateAddressRequest;
import com.voltstack.ecommerce.identity.dto.response.AddressResponse;
import com.voltstack.ecommerce.identity.exception.ResourceNotFoundException;
import com.voltstack.ecommerce.identity.model.Address;
import com.voltstack.ecommerce.identity.repository.AddressRepository;
import com.voltstack.ecommerce.identity.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;

    @Transactional(readOnly = true)
    public List<AddressResponse> listMyAddresses() {
        UUID userId = SecurityUtils.currentUserId();
        return addressRepository.findAllByUserIdOrderByIsDefaultDesc(userId).stream()
                .map(AddressResponse::from)
                .toList();
    }

    @Transactional
    public AddressResponse addAddress(CreateAddressRequest request) {
        UUID userId = SecurityUtils.currentUserId();
        boolean isDefault = Boolean.TRUE.equals(request.getIsDefault());
        if (isDefault) {
            clearDefault(userId);
        }
        Address address = Address.builder()
                .userId(userId)
                .recipientName(request.getRecipientName())
                .phone(request.getPhone())
                .province(request.getProvince())
                .district(request.getDistrict())
                .ward(request.getWard())
                .street(request.getStreet())
                .isDefault(isDefault)
                .build();
        addressRepository.save(address);
        return AddressResponse.from(address);
    }

    @Transactional
    public AddressResponse updateAddress(UUID id, CreateAddressRequest request) {
        UUID userId = SecurityUtils.currentUserId();
        Address address = addressRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.ADDRESS_NOT_FOUND));

        boolean isDefault = Boolean.TRUE.equals(request.getIsDefault());
        if (isDefault && !address.isDefault()) {
            clearDefault(userId);
        }
        address.setRecipientName(request.getRecipientName());
        address.setPhone(request.getPhone());
        address.setProvince(request.getProvince());
        address.setDistrict(request.getDistrict());
        address.setWard(request.getWard());
        address.setStreet(request.getStreet());
        address.setDefault(isDefault);
        addressRepository.save(address);
        return AddressResponse.from(address);
    }

    @Transactional
    public void deleteAddress(UUID id) {
        UUID userId = SecurityUtils.currentUserId();
        Address address = addressRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.ADDRESS_NOT_FOUND));
        addressRepository.delete(address);
    }

    private void clearDefault(UUID userId) {
        addressRepository.findAllByUserIdOrderByIsDefaultDesc(userId).forEach(a -> {
            if (a.isDefault()) {
                a.setDefault(false);
                addressRepository.save(a);
            }
        });
    }
}
