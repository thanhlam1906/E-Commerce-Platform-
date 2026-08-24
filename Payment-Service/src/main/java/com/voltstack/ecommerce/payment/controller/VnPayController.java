package com.voltstack.ecommerce.payment.controller;

import com.voltstack.ecommerce.payment.exception.ResourceNotFoundException;
import com.voltstack.ecommerce.payment.exception.WebhookSignatureException;
import com.voltstack.ecommerce.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * VNPay callbacks (SRS §4). VNPay sends the notification as query params — the signature
 * (vnp_SecureHash) travels inside the query, so these endpoints have no JSON body and no header.
 * Both are public (see SecurityConfig): the IPN is server-to-server, the return URL is a browser redirect.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class VnPayController {

    private final PaymentService paymentService;

    /**
     * VNPay IPN (server-to-server). Must always answer HTTP 200 with an RspCode; a non-00 RspCode
     * makes VNPay retry, so invalid signatures (97) and unknown orders (01) are answered cleanly.
     */
    @RequestMapping(value = "/webhooks/vnpay/ipn", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, String>> ipn(@RequestParam Map<String, String> params) {
        Map<String, String> resp = new HashMap<>();
        try {
            paymentService.handleVnPayNotify(params);
            resp.put("RspCode", "00");
            resp.put("Message", "Confirm Success");
        } catch (WebhookSignatureException e) {
            log.warn("VNPay IPN signature invalid: {}", e.getMessage());
            resp.put("RspCode", "97");
            resp.put("Message", "Invalid Signature");
        } catch (ResourceNotFoundException e) {
            log.warn("VNPay IPN order not found: {}", e.getMessage());
            resp.put("RspCode", "01");
            resp.put("Message", "Order Not Found");
        } catch (RuntimeException e) {
            log.warn("VNPay IPN processing failed: {}", e.getMessage());
            resp.put("RspCode", "99");
            resp.put("Message", "Unknown Error");
        }
        return ResponseEntity.ok(resp);
    }

    /** VNPay return URL — browser redirect back after payment. Applies the result idempotently. */
    @GetMapping(value = "/api/v1/payments/vnpay/return", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> returnUrl(@RequestParam Map<String, String> params) {
        String status;
        String message;
        try {
            paymentService.handleVnPayNotify(params);
            status = switch (params.getOrDefault("vnp_ResponseCode", "")) {
                case "00" -> "SUCCESS";
                case "24" -> "CANCELLED";
                default -> "FAILED";
            };
            message = switch (status) {
                case "SUCCESS" -> "Thanh toán thành công. Cảm ơn bạn đã mua hàng!";
                case "CANCELLED" -> "Bạn đã hủy thanh toán. Đơn hàng chưa được thanh toán.";
                default -> "Thanh toán không thành công. Vui lòng thử lại.";
            };
        } catch (WebhookSignatureException e) {
            status = "INVALID";
            message = "Chữ ký xác thực từ VNPay không hợp lệ.";
        } catch (RuntimeException e) {
            status = "ERROR";
            message = "Không thể cập nhật trạng thái thanh toán.";
        }
        return ResponseEntity.ok()
                .header("X-Frame-Options", "DENY")
                .body(html(status, message, params.get("vnp_OrderInfo")));
    }

    private String html(String status, String message, String orderInfo) {
        return "<!doctype html><html lang=\"vi\"><head><meta charset=\"utf-8\">"
                + "<title>Kết quả thanh toán</title></head><body style=\"font-family:sans-serif;max-width:480px;margin:48px auto;text-align:center\">"
                + "<h2>" + status + "</h2>"
                + (orderInfo == null || orderInfo.isBlank() ? "" : "<p>" + esc(orderInfo) + "</p>")
                + "<p>" + esc(message) + "</p>"
                + "<p><a href=\"/\">Quay lại cửa hàng</a></p>"
                + "</body></html>";
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
