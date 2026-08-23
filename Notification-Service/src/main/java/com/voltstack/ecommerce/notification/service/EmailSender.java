package com.voltstack.ecommerce.notification.service;

import com.voltstack.ecommerce.notification.service.EmailRenderer.RenderedEmail;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EmailSender {

    private final JavaMailSender mailSender;

    @Value("${notification.email.provider:log}")
    private String provider;

    @Value("${notification.email.from:no-reply@localhost}")
    private String from;

    public EmailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Delivers an email. The default {@code log} provider records only metadata
     * (recipient, template, event id) and never the body or verification/reset links.
     */
    public String send(String to, String eventId, String template, RenderedEmail email) {
        if ("smtp".equalsIgnoreCase(provider)) {
            return sendSmtp(to, email);
        }
        log.info("Email notification sent [provider=log] recipient={} template={} eventId={} (body suppressed)",
                to, template, eventId);
        return "log-simulated";
    }

    private String sendSmtp(String to, RenderedEmail email) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(email.subject());
            helper.setText(email.text(), email.html());
            mailSender.send(mime);
            return null;
        } catch (jakarta.mail.MessagingException e) {
            throw new RuntimeException("SMTP send failed for " + to, e);
        }
    }
}
