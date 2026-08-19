package com.voltstack.ecommerce.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Synchronous create-transaction body from Order-Service (SRS §3).
 * {@code returnUrl} is optional — when absent the sandbox gateway uses its own configured base.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentRequest {

    @NotNull(message = "orderId không được để trống")
    private UUID orderId;

    @NotNull(message = "userId không được để trống")
    private UUID userId;

    @NotNull(message = "amount không được để trống")
    @DecimalMin(value = "0.01", message = "Số tiền phải lớn hơn 0")
    @Digits(integer = 10, fraction = 2, message = "Số tiền tối đa 10 chữ số nguyên và 2 chữ số thập phân")
    private BigDecimal amount;

    @NotBlank(message = "currency không được để trống")
    @Pattern(regexp = "^[A-Z]{3}$", message = "currency phải là mã ISO 4217 gồm 3 chữ cái")
    private String currency;

    @NotBlank(message = "paymentMethod không được để trống")
    private String paymentMethod;

    @NotBlank(message = "gateway không được để trống")
    private String gateway;

    private String returnUrl;
}
