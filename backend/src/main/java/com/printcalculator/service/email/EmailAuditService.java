package com.printcalculator.service.email;

import com.printcalculator.dto.AdminEmailLogDto;
import com.printcalculator.entity.CustomQuoteRequest;
import com.printcalculator.entity.EmailLog;
import com.printcalculator.entity.Order;
import com.printcalculator.repository.EmailLogRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class EmailAuditService {
    public static final String SCOPE_ORDER = "ORDER";
    public static final String SCOPE_CONTACT_REQUEST = "CONTACT_REQUEST";
    public static final String ORIGIN_SYSTEM = "SYSTEM";
    public static final String ORIGIN_ADMIN = "ADMIN";

    public static final String EVENT_ORDER_CONFIRMATION_CUSTOMER = "ORDER_CONFIRMATION_CUSTOMER";
    public static final String EVENT_ORDER_NOTIFICATION_ADMIN = "ORDER_NOTIFICATION_ADMIN";
    public static final String EVENT_PAYMENT_REPORTED_CUSTOMER = "PAYMENT_REPORTED_CUSTOMER";
    public static final String EVENT_PAYMENT_CONFIRMED_CUSTOMER = "PAYMENT_CONFIRMED_CUSTOMER";
    public static final String EVENT_ORDER_SHIPPED_CUSTOMER = "ORDER_SHIPPED_CUSTOMER";
    public static final String EVENT_CONTACT_REQUEST_ADMIN = "CONTACT_REQUEST_ADMIN";
    public static final String EVENT_CONTACT_REQUEST_CUSTOMER = "CONTACT_REQUEST_CUSTOMER";

    private final EmailLogRepository emailLogRepository;
    private final EntityManager entityManager;

    public EmailAuditService(EmailLogRepository emailLogRepository, EntityManager entityManager) {
        this.emailLogRepository = emailLogRepository;
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EmailLog recordOrderEmail(Order order,
                                     String eventType,
                                     String origin,
                                     String recipient,
                                     String subject,
                                     String templateName,
                                     String attachmentName,
                                     EmailSendResult result,
                                     UUID resentFromEmailLogId) {
        EmailLog log = buildBaseLog(SCOPE_ORDER, eventType, origin, recipient, subject, templateName, attachmentName, result, resentFromEmailLogId);
        if (order != null && order.getId() != null) {
            log.setOrder(entityManager.getReference(Order.class, order.getId()));
        }
        return emailLogRepository.save(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EmailLog recordContactRequestEmail(CustomQuoteRequest request,
                                              String eventType,
                                              String origin,
                                              String recipient,
                                              String subject,
                                              String templateName,
                                              EmailSendResult result,
                                              UUID resentFromEmailLogId) {
        EmailLog log = buildBaseLog(SCOPE_CONTACT_REQUEST, eventType, origin, recipient, subject, templateName, null, result, resentFromEmailLogId);
        if (request != null && request.getId() != null) {
            log.setContactRequest(entityManager.getReference(CustomQuoteRequest.class, request.getId()));
        }
        return emailLogRepository.save(log);
    }

    public List<AdminEmailLogDto> getOrderEmailLogDtos(UUID orderId) {
        return emailLogRepository.findByOrder_IdOrderByAttemptedAtDesc(orderId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<AdminEmailLogDto> getContactRequestEmailLogDtos(UUID requestId) {
        return emailLogRepository.findByContactRequest_IdOrderByAttemptedAtDesc(requestId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public AdminEmailLogDto toDto(EmailLog log) {
        AdminEmailLogDto dto = new AdminEmailLogDto();
        dto.setId(log.getId());
        dto.setScope(log.getScope());
        dto.setEventType(log.getEventType());
        dto.setOrigin(log.getOrigin());
        dto.setStatus(log.getStatus());
        dto.setRecipient(log.getRecipient());
        dto.setSubject(log.getSubject());
        dto.setTemplateName(log.getTemplateName());
        dto.setAttachmentName(log.getAttachmentName());
        dto.setErrorMessage(log.getErrorMessage());
        dto.setAttemptedAt(log.getAttemptedAt());
        dto.setSentAt(log.getSentAt());
        dto.setResentFromEmailLogId(log.getResentFromEmailLogId());
        return dto;
    }

    private EmailLog buildBaseLog(String scope,
                                  String eventType,
                                  String origin,
                                  String recipient,
                                  String subject,
                                  String templateName,
                                  String attachmentName,
                                  EmailSendResult result,
                                  UUID resentFromEmailLogId) {
        EmailSendResult safeResult = result != null
                ? result
                : EmailSendResult.failed(OffsetDateTime.now(), "Email service returned no result.");

        EmailLog log = new EmailLog();
        log.setScope(scope);
        log.setEventType(eventType);
        log.setOrigin(origin);
        log.setRecipient(isBlank(recipient) ? "-" : recipient);
        log.setSubject(subject);
        log.setTemplateName(templateName);
        log.setAttachmentName(attachmentName);
        log.setStatus(safeResult.status());
        log.setAttemptedAt(safeResult.attemptedAt());
        log.setSentAt(safeResult.sentAt());
        log.setErrorMessage(safeResult.errorMessage());
        log.setResentFromEmailLogId(resentFromEmailLogId);
        return log;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
