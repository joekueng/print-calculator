package com.printcalculator.service.request;

import com.printcalculator.entity.CustomQuoteRequest;
import com.printcalculator.service.email.EmailAuditService;
import com.printcalculator.service.email.EmailNotificationService;
import com.printcalculator.service.email.EmailSendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;

@ExtendWith(MockitoExtension.class)
class CustomQuoteRequestNotificationServiceTest {

    @Mock
    private EmailNotificationService emailNotificationService;

    @Mock
    private EmailAuditService emailAuditService;

    private ContactRequestLocalizationService localizationService;
    private CustomQuoteRequestNotificationService service;

    @BeforeEach
    void setUp() {
        localizationService = new ContactRequestLocalizationService();
        service = new CustomQuoteRequestNotificationService(emailNotificationService, localizationService, emailAuditService);
    }

    @Test
    void sendNotifications_withAdminAndCustomerEnabled_shouldSendBothEmails() {
        ReflectionTestUtils.setField(service, "contactRequestAdminMailEnabled", true);
        ReflectionTestUtils.setField(service, "contactRequestAdminMailAddress", "admin@3d-fab.ch");
        ReflectionTestUtils.setField(service, "contactRequestCustomerMailEnabled", true);

        CustomQuoteRequest request = buildRequest();

        service.sendNotifications(request, 3, "en-US");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> dataCaptor = (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> toCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> templateCaptor = ArgumentCaptor.forClass(String.class);

        verify(emailNotificationService, times(2)).sendEmail(
                toCaptor.capture(),
                subjectCaptor.capture(),
                templateCaptor.capture(),
                dataCaptor.capture()
        );

        List<String> recipients = toCaptor.getAllValues();
        assertTrue(recipients.contains("admin@3d-fab.ch"));
        assertTrue(recipients.contains("customer@example.com"));

        int customerIndex = recipients.indexOf("customer@example.com");
        assertEquals("contact-request-customer", templateCaptor.getAllValues().get(customerIndex));
        assertEquals("We received your contact request #" + request.getId() + " - 3D-Fab", subjectCaptor.getAllValues().get(customerIndex));
        assertEquals("Date", dataCaptor.getAllValues().get(customerIndex).get("labelDate"));

        int adminIndex = recipients.indexOf("admin@3d-fab.ch");
        assertEquals("contact-request-admin", templateCaptor.getAllValues().get(adminIndex));
        assertEquals(3, dataCaptor.getAllValues().get(adminIndex).get("attachmentsCount"));

        verify(emailAuditService).recordContactRequestEmail(
                eq(request),
                eq(EmailAuditService.EVENT_CONTACT_REQUEST_ADMIN),
                eq(EmailAuditService.ORIGIN_SYSTEM),
                eq("admin@3d-fab.ch"),
                eq("Nuova richiesta di contatto #" + request.getId()),
                eq("contact-request-admin"),
                nullable(EmailSendResult.class),
                isNull()
        );
        verify(emailAuditService).recordContactRequestEmail(
                eq(request),
                eq(EmailAuditService.EVENT_CONTACT_REQUEST_CUSTOMER),
                eq(EmailAuditService.ORIGIN_SYSTEM),
                eq("customer@example.com"),
                eq("We received your contact request #" + request.getId() + " - 3D-Fab"),
                eq("contact-request-customer"),
                nullable(EmailSendResult.class),
                isNull()
        );
    }

    @Test
    void sendNotifications_withCustomerDisabled_shouldOnlySendAdminEmail() {
        ReflectionTestUtils.setField(service, "contactRequestAdminMailEnabled", true);
        ReflectionTestUtils.setField(service, "contactRequestAdminMailAddress", "admin@3d-fab.ch");
        ReflectionTestUtils.setField(service, "contactRequestCustomerMailEnabled", false);

        service.sendNotifications(buildRequest(), 1, "it");

        verify(emailNotificationService, times(1)).sendEmail(
                org.mockito.ArgumentMatchers.eq("admin@3d-fab.ch"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("contact-request-admin"),
                org.mockito.ArgumentMatchers.anyMap()
        );
    }

    @Test
    void sendNotifications_withMissingAdminAddressAndCustomerDisabled_shouldSendNothing() {
        ReflectionTestUtils.setField(service, "contactRequestAdminMailEnabled", true);
        ReflectionTestUtils.setField(service, "contactRequestAdminMailAddress", " ");
        ReflectionTestUtils.setField(service, "contactRequestCustomerMailEnabled", false);

        service.sendNotifications(buildRequest(), 1, "fr");

        verify(emailNotificationService, never()).sendEmail(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyMap()
        );
    }

    private CustomQuoteRequest buildRequest() {
        CustomQuoteRequest request = new CustomQuoteRequest();
        request.setId(UUID.randomUUID());
        request.setRequestType("PRINT_SERVICE");
        request.setCustomerType("PRIVATE");
        request.setName("Mario Rossi");
        request.setCompanyName("3D Fab SA");
        request.setContactPerson("Mario Rossi");
        request.setEmail("customer@example.com");
        request.setPhone("+41910000000");
        request.setMessage("Vorrei una quotazione.");
        request.setCreatedAt(OffsetDateTime.parse("2026-03-05T10:15:30+01:00"));
        return request;
    }
}
