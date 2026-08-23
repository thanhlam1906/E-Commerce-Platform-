package com.voltstack.ecommerce.order.controller;

import com.voltstack.ecommerce.order.dto.request.AddCartItemRequest;
import com.voltstack.ecommerce.order.dto.request.UpdateCartItemRequest;
import com.voltstack.ecommerce.order.dto.response.ApiDataResponse;
import com.voltstack.ecommerce.order.dto.response.CartResponse;
import com.voltstack.ecommerce.order.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ApiDataResponse<CartResponse> getCart(@RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        return ApiDataResponse.ok(cartService.getCart(sessionId));
    }

    @PostMapping("/items")
    public ApiDataResponse<CartResponse> addItem(@RequestHeader(value = "X-Session-Id", required = false) String sessionId,
                                                 @Valid @RequestBody AddCartItemRequest req) {
        return ApiDataResponse.ok(cartService.addItem(sessionId, req));
    }

    @PutMapping("/items/{sku}")
    public ApiDataResponse<CartResponse> updateItem(@RequestHeader(value = "X-Session-Id", required = false) String sessionId,
                                                    @PathVariable String sku,
                                                    @Valid @RequestBody UpdateCartItemRequest req) {
        return ApiDataResponse.ok(cartService.updateItem(sessionId, sku, req));
    }

    @DeleteMapping("/items/{sku}")
    public ApiDataResponse<CartResponse> removeItem(@RequestHeader(value = "X-Session-Id", required = false) String sessionId,
                                                    @PathVariable String sku) {
        return ApiDataResponse.ok(cartService.removeItem(sessionId, sku));
    }

    @PostMapping("/merge")
    public ApiDataResponse<CartResponse> mergeCart(@RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        return ApiDataResponse.ok(cartService.mergeCart(sessionId));
    }
}
