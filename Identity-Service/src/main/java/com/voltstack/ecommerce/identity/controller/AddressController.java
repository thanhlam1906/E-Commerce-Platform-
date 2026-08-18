package com.voltstack.ecommerce.identity.controller;

import com.voltstack.ecommerce.identity.dto.request.CreateAddressRequest;
import com.voltstack.ecommerce.identity.dto.response.AddressResponse;
import com.voltstack.ecommerce.identity.dto.response.ApiDataResponse;
import com.voltstack.ecommerce.identity.service.AddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    public ResponseEntity<ApiDataResponse<List<AddressResponse>>> list() {
        return ResponseEntity.ok(ApiDataResponse.ok(addressService.listMyAddresses()));
    }

    @PostMapping
    public ResponseEntity<ApiDataResponse<AddressResponse>> create(@Valid @RequestBody CreateAddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiDataResponse.created(addressService.addAddress(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiDataResponse<AddressResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody CreateAddressRequest request) {
        return ResponseEntity.ok(ApiDataResponse.ok(addressService.updateAddress(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiDataResponse<Void>> delete(@PathVariable UUID id) {
        addressService.deleteAddress(id);
        return ResponseEntity.ok(ApiDataResponse.ok(null));
    }
}
