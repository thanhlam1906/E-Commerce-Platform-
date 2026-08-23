package com.voltstack.ecommerce.payment.controller;

import com.voltstack.ecommerce.payment.dto.ApiDataResponse;
import com.voltstack.ecommerce.payment.dto.TransactionResponse;
import com.voltstack.ecommerce.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentHistoryController {

    private final PaymentService paymentService;

    @GetMapping("/history")
    public ApiDataResponse<Page<TransactionResponse>> history(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiDataResponse.ok(paymentService.history(pageable));
    }
}
