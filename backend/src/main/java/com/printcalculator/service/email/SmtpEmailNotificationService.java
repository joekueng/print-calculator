package com.printcalculator.service.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.springframework.core.io.ByteArrayResource;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmtpEmailNotificationService implements EmailNotificationService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;
    private static final Pattern SENSITIVE_VALUE_PATTERN = Pattern.compile(
            "(?i)(password|passwd|pwd|secret|api[_-]?key|token|authorization|basic)\\s*[=:]\\s*\\S+"
    );

    private final JavaMailSender emailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    @Override
    public EmailSendResult sendEmail(String to, String subject, String templateName, Map<String, Object> contextData) {
        return sendEmailWithAttachment(to, subject, templateName, contextData, null, null);
    }

    @Override
    public EmailSendResult sendEmailWithAttachment(String to, String subject, String templateName, Map<String, Object> contextData, String attachmentName, byte[] attachmentData) {
        OffsetDateTime attemptedAt = OffsetDateTime.now();
        if (!mailEnabled) {
            log.info("Email sending disabled (app.mail.enabled=false). Skipping email to {}", to);
            return EmailSendResult.skipped(attemptedAt, "Email sending disabled (app.mail.enabled=false).");
        }

        log.info("Preparing to send email to {} with template {}", to, templateName);

        try {
            Context context = new Context();
            context.setVariables(contextData);

            String process = templateEngine.process("email/" + templateName, context);
            MimeMessage mimeMessage = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(process, true); // true indicates HTML format

            if (attachmentName != null && attachmentData != null) {
                helper.addAttachment(attachmentName, new ByteArrayResource(attachmentData));
            }

            emailSender.send(mimeMessage);
            log.info("Email successfully sent to {}", to);
            return EmailSendResult.sent(attemptedAt, OffsetDateTime.now());

        } catch (MessagingException e) {
            log.error("Failed to send email to {}", to, e);
            // Non blocco l'ordine se l'email fallisce, ma loggo l'errore adeguatamente.
            return EmailSendResult.failed(attemptedAt, errorMessage(e));
        } catch (Exception e) {
            log.error("Unexpected error while sending email to {}", to, e);
            return EmailSendResult.failed(attemptedAt, errorMessage(e));
        }
    }

    private String errorMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }

        String sanitized = SENSITIVE_VALUE_PATTERN.matcher(message).replaceAll("$1=***");
        sanitized = sanitized.replaceAll("[\\p{Cntrl}]", " ").trim();
        if (sanitized.length() > MAX_ERROR_MESSAGE_LENGTH) {
            sanitized = sanitized.substring(0, MAX_ERROR_MESSAGE_LENGTH).trim() + "...";
        }
        return sanitized;
    }
}
