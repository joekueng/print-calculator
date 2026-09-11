package com.printcalculator.service.email;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmtpEmailNotificationServiceTest {

    @Mock
    private JavaMailSender emailSender;

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private MimeMessage mimeMessage;

    @InjectMocks
    private SmtpEmailNotificationService emailNotificationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailNotificationService, "fromAddress", "noreply@test.com");
        ReflectionTestUtils.setField(emailNotificationService, "mailEnabled", true);
    }

    @Test
    void sendEmail_Success() {
        // Arrange
        String to = "user@test.com";
        String subject = "Test Subject";
        String templateName = "test-template";
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("key", "value");

        when(templateEngine.process(eq("email/" + templateName), any(Context.class))).thenReturn("<html>Test</html>");
        when(emailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        EmailSendResult result = emailNotificationService.sendEmail(to, subject, templateName, contextData);

        // Assert
        assertEquals(EmailSendResult.STATUS_SENT, result.status());
        verify(templateEngine, times(1)).process(eq("email/" + templateName), any(Context.class));
        verify(emailSender, times(1)).createMimeMessage();
        verify(emailSender, times(1)).send(mimeMessage);
    }

    @Test
    void sendEmail_Exception_ShouldNotThrow() {
        // Arrange
        String to = "user@test.com";
        String subject = "Test Subject";
        String templateName = "test-template";
        Map<String, Object> contextData = new HashMap<>();

        when(templateEngine.process(eq("email/" + templateName), any(Context.class))).thenThrow(new RuntimeException("Template error"));

        // Act & Assert
        // We expect the exception to be caught and logged, not propagated
        EmailSendResult result = assertDoesNotThrow(() -> emailNotificationService.sendEmail(to, subject, templateName, contextData));
        assertEquals(EmailSendResult.STATUS_FAILED, result.status());
        
        verify(emailSender, never()).createMimeMessage();
        verify(emailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendEmail_WithSensitiveValuesInError_shouldRedactSecrets() {
        String to = "user@test.com";
        Map<String, Object> contextData = new HashMap<>();

        when(templateEngine.process(eq("email/order-confirmation"), any(Context.class)))
                .thenThrow(new RuntimeException("535 Authentication failed: password=super-secret token=abc123 for user info@3d-fab.ch"));

        EmailSendResult result = assertDoesNotThrow(
                () -> emailNotificationService.sendEmail(to, "subject", "order-confirmation", contextData)
        );

        assertEquals(EmailSendResult.STATUS_FAILED, result.status());
        assertFalse(result.errorMessage().contains("super-secret"));
        assertFalse(result.errorMessage().contains("abc123"));
        assertEquals("535 Authentication failed: password=*** token=*** for user info@3d-fab.ch", result.errorMessage());
    }

    @Test
    void sendEmail_WithControlCharactersInError_shouldSanitize() {
        String to = "user@test.com";
        Map<String, Object> contextData = new HashMap<>();

        when(templateEngine.process(eq("email/order-confirmation"), any(Context.class)))
                .thenThrow(new RuntimeException("Connection refused\r\n\n\t at org.smtp.Internal"));

        EmailSendResult result = assertDoesNotThrow(
                () -> emailNotificationService.sendEmail(to, "subject", "order-confirmation", contextData)
        );

        assertEquals(EmailSendResult.STATUS_FAILED, result.status());
        assertFalse(result.errorMessage().contains("\r"));
        assertFalse(result.errorMessage().contains("\n"));
        assertFalse(result.errorMessage().contains("\t"));
    }

    @Test
    void sendEmail_WithVeryLongError_shouldTruncate() {
        String to = "user@test.com";
        Map<String, Object> contextData = new HashMap<>();
        String longMessage = "x".repeat(10_000);

        when(templateEngine.process(eq("email/order-confirmation"), any(Context.class)))
                .thenThrow(new RuntimeException(longMessage));

        EmailSendResult result = assertDoesNotThrow(
                () -> emailNotificationService.sendEmail(to, "subject", "order-confirmation", contextData)
        );

        assertEquals(EmailSendResult.STATUS_FAILED, result.status());
        assertFalse(result.errorMessage().contains("x".repeat(10_000)));
        assertTrue(result.errorMessage().length() <= 503);
        assertTrue(result.errorMessage().endsWith("..."));
    }
}
