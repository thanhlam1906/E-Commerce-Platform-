package com.voltstack.ecommerce.payment.controller;

import com.voltstack.ecommerce.payment.dto.CreatePaymentRequest;
import com.voltstack.ecommerce.payment.dto.CreatePaymentResponse;
import com.voltstack.ecommerce.payment.dto.RefundRequest;
import com.voltstack.ecommerce.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class InternalPaymentController {

    private final PaymentService paymentService;

    @PostMapping("/internal/payments")
    public CreatePaymentResponse createPayment(@Valid @RequestBody CreatePaymentRequest req) {
        return paymentService.createPayment(req);
    }

    @PostMapping("/internal/payments/{txnId}/refund")
    public ResponseEntity<Void> refund(@PathVariable UUID txnId, @Valid @RequestBody RefundRequest req) {
        paymentService.refund(txnId, req.getOrderId());
        return ResponseEntity.ok().build();
    }
}
