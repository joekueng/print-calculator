package com.printcalculator.service.request;

import com.printcalculator.entity.EmailLog;
import com.printcalculator.entity.CustomQuoteRequest;
import com.printcalculator.service.email.EmailAuditService;
import com.printcalculator.service.email.EmailNotificationService;
import com.printcalculator.service.email.EmailSendResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class CustomQuoteRequestNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(CustomQuoteRequestNotificationService.class);

    private final EmailNotificationService emailNotificationService;
    private final ContactRequestLocalizationService localizationService;
    private final EmailAuditService emailAuditService;

    @Value("${app.mail.contact-request.admin.enabled:true}")
    private boolean contactRequestAdminMailEnabled;

    @Value("${app.mail.contact-request.admin.address:infog@3d-fab.ch}")
    private String contactRequestAdminMailAddress;

    @Value("${app.mail.contact-request.customer.enabled:true}")
    private boolean contactRequestCustomerMailEnabled;

    @Value("${app.frontend.base-url:http://localhost:4200}")
    private String frontendBaseUrl;

    public CustomQuoteRequestNotificationService(EmailNotificationService emailNotificationService,
                                                 ContactRequestLocalizationService localizationService,
                                                 EmailAuditService emailAuditService) {
        this.emailNotificationService = emailNotificationService;
        this.localizationService = localizationService;
        this.emailAuditService = emailAuditService;
    }

    public void sendNotifications(CustomQuoteRequest request, int attachmentsCount, String rawLanguage) {
        String language = localizationService.normalizeLanguage(rawLanguage);
        sendAdminContactRequestNotification(request, attachmentsCount, EmailAuditService.ORIGIN_SYSTEM, null);
        sendCustomerContactRequestConfirmation(request, attachmentsCount, language, EmailAuditService.ORIGIN_SYSTEM, null);
    }

    public EmailLog sendAdminContactRequestNotification(CustomQuoteRequest request,
                                                        int attachmentsCount,
                                                        String origin,
                                                        UUID resentFromEmailLogId) {
        if (!contactRequestAdminMailEnabled) {
            return recordSkippedContactEmail(
                    request,
                    EmailAuditService.EVENT_CONTACT_REQUEST_ADMIN,
                    origin,
                    contactRequestAdminMailAddress,
                    null,
                    "contact-request-admin",
                    "Contact request admin notification disabled.",
                    resentFromEmailLogId
            );
        }
        if (contactRequestAdminMailAddress == null || contactRequestAdminMailAddress.isBlank()) {
            logger.warn("Contact request admin notification enabled but no admin address configured.");
            return recordSkippedContactEmail(
                    request,
                    EmailAuditService.EVENT_CONTACT_REQUEST_ADMIN,
                    origin,
                    contactRequestAdminMailAddress,
                    null,
                    "contact-request-admin",
                    "Contact request admin notification address is missing.",
                    resentFromEmailLogId
            );
        }

        Map<String, Object> templateData = new HashMap<>();
        templateData.put("requestId", request.getId());
        templateData.put("createdAt", request.getCreatedAt());
        templateData.put("requestType", safeValue(request.getRequestType()));
        templateData.put("customerType", safeValue(request.getCustomerType()));
        templateData.put("name", safeValue(request.getName()));
        templateData.put("companyName", safeValue(request.getCompanyName()));
        templateData.put("contactPerson", safeValue(request.getContactPerson()));
        templateData.put("email", safeValue(request.getEmail()));
        templateData.put("phone", safeValue(request.getPhone()));
        templateData.put("message", safeValue(request.getMessage()));
        templateData.put("attachmentsCount", attachmentsCount);
        templateData.put("logoUrl", buildLogoUrl());
        templateData.put("currentYear", Year.now().getValue());
        String subject = "Nuova richiesta di contatto #" + request.getId();

        EmailSendResult result = emailNotificationService.sendEmail(
                contactRequestAdminMailAddress,
                subject,
                "contact-request-admin",
                templateData
        );

        return emailAuditService.recordContactRequestEmail(
                request,
                EmailAuditService.EVENT_CONTACT_REQUEST_ADMIN,
                origin,
                contactRequestAdminMailAddress,
                subject,
                "contact-request-admin",
                result,
                resentFromEmailLogId
        );
    }

    public EmailLog sendCustomerContactRequestConfirmation(CustomQuoteRequest request,
                                                           int attachmentsCount,
                                                           String language,
                                                           String origin,
                                                           UUID resentFromEmailLogId) {
        if (!contactRequestCustomerMailEnabled) {
            return recordSkippedContactEmail(
                    request,
                    EmailAuditService.EVENT_CONTACT_REQUEST_CUSTOMER,
                    origin,
                    request.getEmail(),
                    null,
                    "contact-request-customer",
                    "Contact request customer confirmation disabled.",
                    resentFromEmailLogId
            );
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            logger.warn("Contact request confirmation skipped: missing customer email for request {}", request.getId());
            return recordSkippedContactEmail(
                    request,
                    EmailAuditService.EVENT_CONTACT_REQUEST_CUSTOMER,
                    origin,
                    request.getEmail(),
                    null,
                    "contact-request-customer",
                    "Customer email is missing.",
                    resentFromEmailLogId
            );
        }

        Map<String, Object> templateData = new HashMap<>();
        templateData.put("requestId", request.getId());
        templateData.put(
                "createdAt",
                request.getCreatedAt().format(
                        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                                .withLocale(localizationService.localeForLanguage(language))
                )
        );
        templateData.put("recipientName", localizationService.resolveRecipientName(request, language));
        templateData.put("requestType", localizationService.localizeRequestType(request.getRequestType(), language));
        templateData.put("customerType", localizationService.localizeCustomerType(request.getCustomerType(), language));
        templateData.put("name", safeValue(request.getName()));
        templateData.put("companyName", safeValue(request.getCompanyName()));
        templateData.put("contactPerson", safeValue(request.getContactPerson()));
        templateData.put("email", safeValue(request.getEmail()));
        templateData.put("phone", safeValue(request.getPhone()));
        templateData.put("message", safeValue(request.getMessage()));
        templateData.put("attachmentsCount", attachmentsCount);
        templateData.put("logoUrl", buildLogoUrl());
        templateData.put("currentYear", Year.now().getValue());

        String subject = localizationService.applyCustomerContactRequestTexts(templateData, language, request.getId());

        EmailSendResult result = emailNotificationService.sendEmail(
                request.getEmail(),
                subject,
                "contact-request-customer",
                templateData
        );

        return emailAuditService.recordContactRequestEmail(
                request,
                EmailAuditService.EVENT_CONTACT_REQUEST_CUSTOMER,
                origin,
                request.getEmail(),
                subject,
                "contact-request-customer",
                result,
                resentFromEmailLogId
        );
    }

    public EmailLog resendNotification(CustomQuoteRequest request, int attachmentsCount, EmailLog sourceLog) {
        String eventType = sourceLog.getEventType();
        UUID resentFromEmailLogId = sourceLog.getId();

        return switch (eventType) {
            case EmailAuditService.EVENT_CONTACT_REQUEST_ADMIN ->
                    sendAdminContactRequestNotification(request, attachmentsCount, EmailAuditService.ORIGIN_ADMIN, resentFromEmailLogId);
            case EmailAuditService.EVENT_CONTACT_REQUEST_CUSTOMER ->
                    sendCustomerContactRequestConfirmation(
                            request,
                            attachmentsCount,
                            localizationService.normalizeLanguage(null),
                            EmailAuditService.ORIGIN_ADMIN,
                            resentFromEmailLogId
                    );
            default -> recordSkippedContactEmail(
                    request,
                    eventType,
                    EmailAuditService.ORIGIN_ADMIN,
                    sourceLog.getRecipient(),
                    sourceLog.getSubject(),
                    sourceLog.getTemplateName(),
                    "Unsupported contact request email type: " + eventType,
                    resentFromEmailLogId
            );
        };
    }

    private EmailLog recordSkippedContactEmail(CustomQuoteRequest request,
                                               String eventType,
                                               String origin,
                                               String recipient,
                                               String subject,
                                               String templateName,
                                               String reason,
                                               UUID resentFromEmailLogId) {
        return emailAuditService.recordContactRequestEmail(
                request,
                eventType,
                origin,
                recipient,
                subject,
                templateName,
                EmailSendResult.skipped(OffsetDateTime.now(), reason),
                resentFromEmailLogId
        );
    }

    private String safeValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value;
    }

    private String buildLogoUrl() {
        String baseUrl = frontendBaseUrl == null || frontendBaseUrl.isBlank()
                ? "http://localhost:4200"
                : frontendBaseUrl;
        return baseUrl.replaceAll("/+$", "") + "/assets/images/SVG/logo-giallo-spesso.svg";
    }
}
