package com.voltstack.ecommerce.identity.service;

import com.voltstack.ecommerce.identity.dto.request.CreateAddressRequest;
import com.voltstack.ecommerce.identity.dto.response.AddressResponse;
import com.voltstack.ecommerce.identity.model.Address;
import com.voltstack.ecommerce.identity.repository.AddressRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock
    private AddressRepository addressRepository;

    @InjectMocks
    private AddressService addressService;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(UUID id) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(id.toString(), null, List.of()));
    }

    @Test
    void addAddress_whenDefault_clearsExistingDefault() {
        UUID userId = UUID.randomUUID();
        authenticate(userId);
        Address existingDefault = Address.builder().userId(userId).isDefault(true).build();
        when(addressRepository.findAllByUserIdOrderByIsDefaultDesc(userId))
                .thenReturn(List.of(existingDefault));

        addressService.addAddress(CreateAddressRequest.builder()
                .recipientName("B").phone("0123").isDefault(true).build());

        assertEquals(false, existingDefault.isDefault());

        ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
        verify(addressRepository, times(2)).save(captor.capture());
        Address saved = captor.getAllValues().stream()
                .filter(a -> "B".equals(a.getRecipientName()))
                .findFirst()
                .orElseThrow();
        assertTrue(saved.isDefault());
    }

    @Test
    void addAddress_whenNotDefault_doesNotTouchOthers() {
        UUID userId = UUID.randomUUID();
        authenticate(userId);

        AddressResponse res = addressService.addAddress(CreateAddressRequest.builder()
                .recipientName("B").phone("0123").isDefault(false).build());

        assertEquals(false, res.isDefault());
        verify(addressRepository).save(any(Address.class));
    }

    @Test
    void listMyAddresses_returnsCurrentUserAddresses() {
        UUID userId = UUID.randomUUID();
        authenticate(userId);
        when(addressRepository.findAllByUserIdOrderByIsDefaultDesc(userId))
                .thenReturn(List.of(Address.builder().userId(userId).recipientName("B").build()));

        List<AddressResponse> res = addressService.listMyAddresses();

        assertEquals(1, res.size());
    }
}
