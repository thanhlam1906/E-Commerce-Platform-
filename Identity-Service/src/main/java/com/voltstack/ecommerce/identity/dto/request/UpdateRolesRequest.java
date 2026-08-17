package com.voltstack.ecommerce.identity.dto.request;

import com.voltstack.ecommerce.identity.model.enums.Role;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRolesRequest {

    @NotNull(message = "Role không được để trống")
    private Role role;
}
