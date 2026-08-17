package com.voltstack.ecommerce.identity.dto.response;

import com.voltstack.ecommerce.identity.model.Address;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressResponse {

    private UUID id;
    private String recipientName;
    private String phone;
    private String province;
    private String district;
    private String ward;
    private String street;
    private boolean isDefault;

    public static AddressResponse from(Address address) {
        return AddressResponse.builder()
                .id(address.getId())
                .recipientName(address.getRecipientName())
                .phone(address.getPhone())
                .province(address.getProvince())
                .district(address.getDistrict())
                .ward(address.getWard())
                .street(address.getStreet())
                .isDefault(address.isDefault())
                .build();
    }
}
