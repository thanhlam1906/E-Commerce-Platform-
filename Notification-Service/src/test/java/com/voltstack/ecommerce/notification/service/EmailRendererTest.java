package com.voltstack.ecommerce.notification.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailRendererTest {

    private final EmailRenderer renderer = new EmailRenderer();

    record Case(String template, Map<String, Object> payload, String htmlMarker, String textMarker) {}

    @Test
    void rendersAllSixTemplatesHtmlAndText() {
        List<Case> cases = List.of(
                new Case("order-confirmation",
                        Map.of("orderNumber", "OR-1", "totalAmount", "250000", "currency", "VND",
                                "items", List.of(Map.of("productName", "Giay", "quantity", 1, "subtotal", "250000"))),
                        "OR-1", "OR-1"),
                new Case("order-status-updated",
                        Map.of("orderNumber", "OR-1", "newStatus", "SHIPPING", "oldStatus", "CONFIRMED"),
                        "SHIPPING", "SHIPPING"),
                new Case("order-cancelled",
                        Map.of("orderNumber", "OR-1", "reason", "customer request", "refundAmount", "250000"),
                        "customer request", "250000"),
                new Case("payment-confirmed",
                        Map.of("orderNumber", "OR-1", "amount", "250000", "transactionId", "T-9"),
                        "T-9", "T-9"),
                new Case("email-verification",
                        Map.of("verifyLink", "https://id.local/verify?t=abc"),
                        "https://id.local/verify?t=abc", "https://id.local/verify?t=abc"),
                new Case("password-reset",
                        Map.of("resetLink", "https://id.local/reset?t=xyz"),
                        "https://id.local/reset?t=xyz", "https://id.local/reset?t=xyz"));

        for (Case c : cases) {
            EmailRenderer.RenderedEmail email = renderer.render(c.template, c.payload);
            assertNotNull(email.subject(), c.template + " subject");
            assertTrue(email.html().contains(c.htmlMarker), c.template + " html missing '" + c.htmlMarker + "'");
            assertTrue(email.text().contains(c.textMarker), c.template + " text missing '" + c.textMarker + "'");
        }
    }

    /**
     * Documents current behavior (NOT the SRS NOT-007d ideal): a missing variable renders as
     * an empty string instead of raising {@link com.voltstack.ecommerce.notification.exception.PermanentFailureException}.
     * The acceptance criterion "template thiếu biến → DLQ ngay" is therefore not implemented.
     */
    @Test
    void missingVariable_rendersEmptyWithoutThrowing() {
        EmailRenderer.RenderedEmail email =
                renderer.render("payment-confirmed", Map.of("orderNumber", "OR-1", "amount", "100"));
        assertTrue(email.html().contains("Mã giao dịch"));
        assertFalse(email.html().contains("T-9"));
    }
}
