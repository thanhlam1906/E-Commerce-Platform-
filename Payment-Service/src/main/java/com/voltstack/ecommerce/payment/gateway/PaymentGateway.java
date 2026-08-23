package com.voltstack.ecommerce.payment.gateway;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Third-party payment gateway adapter (SRS §7). Real VNPay/MoMo/Stripe adapters are
 * credential-gated follow-ups; today only {@link SandboxPaymentGateway} exists.
 * Implementations must honour the ≤5s gateway-call timeout from SRS §3.
 */
public interface PaymentGateway {

    GatewayResult createPayment(UUID transactionId, BigDecimal amount, String currency, String returnUrl);
}
