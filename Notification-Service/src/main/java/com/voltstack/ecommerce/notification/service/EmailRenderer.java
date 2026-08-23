package com.voltstack.ecommerce.notification.service;

import com.voltstack.ecommerce.notification.exception.PermanentFailureException;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.HashMap;
import java.util.Map;

/** Renders the 6 SRS templates (HTML + text/plain fallback) from classpath:/templates/email/. */
@Component
public class EmailRenderer {

    private static final Map<String, String> SUBJECTS = Map.of(
            "order-confirmation", "Đơn hàng của bạn đã được tạo",
            "order-status-updated", "Cập nhật trạng thái đơn hàng",
            "order-cancelled", "Đơn hàng của bạn đã bị hủy",
            "payment-confirmed", "Thanh toán thành công",
            "email-verification", "Xác thực địa chỉ email",
            "password-reset", "Đặt lại mật khẩu");

    private final TemplateEngine htmlEngine;
    private final TemplateEngine textEngine;

    public EmailRenderer() {
        this.htmlEngine = engine(TemplateMode.HTML, ".html");
        this.textEngine = engine(TemplateMode.TEXT, ".txt");
    }

    public RenderedEmail render(String template, Map<String, Object> payload) {
        Map<String, Object> model = new HashMap<>();
        if (payload != null) {
            model.putAll(payload);
        }
        alias(model, "paymentUrl", "payment_url");
        alias(model, "verifyLink", "verify_link");
        alias(model, "resetLink", "reset_link");
        Context ctx = new Context();
        ctx.setVariables(model);
        String html;
        String text;
        try {
            html = htmlEngine.process(template, ctx);
            text = textEngine.process(template, ctx);
        } catch (RuntimeException e) {
            throw new PermanentFailureException("Template render failed for '" + template + "': " + e.getMessage(), e);
        }
        return new RenderedEmail(SUBJECTS.getOrDefault(template, "VoltStack Ecommerce"), html, text);
    }

    private static TemplateEngine engine(TemplateMode mode, String suffix) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/email/");
        resolver.setSuffix(suffix);
        resolver.setTemplateMode(mode);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private static void alias(Map<String, Object> model, String camel, String snake) {
        if (model.containsKey(camel) && !model.containsKey(snake)) {
            model.put(snake, model.get(camel));
        }
    }

    public record RenderedEmail(String subject, String html, String text) {
    }
}
