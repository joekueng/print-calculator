package com.printcalculator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "email_logs", indexes = {
        @Index(name = "ix_email_logs_order_id", columnList = "order_id"),
        @Index(name = "ix_email_logs_contact_request_id", columnList = "contact_request_id"),
        @Index(name = "ix_email_logs_attempted_at", columnList = "attempted_at")
})
public class EmailLog {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "email_log_id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_request_id")
    private CustomQuoteRequest contactRequest;

    @Column(name = "scope", nullable = false, length = 32)
    private String scope;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "origin", nullable = false, length = 32)
    private String origin;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "recipient", nullable = false, length = Integer.MAX_VALUE)
    private String recipient;

    @Column(name = "subject", length = Integer.MAX_VALUE)
    private String subject;

    @Column(name = "template_name", length = 128)
    private String templateName;

    @Column(name = "attachment_name", length = Integer.MAX_VALUE)
    private String attachmentName;

    @Column(name = "error_message", length = Integer.MAX_VALUE)
    private String errorMessage;

    @Column(name = "attempted_at", nullable = false)
    private OffsetDateTime attemptedAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "resent_from_email_log_id")
    private UUID resentFromEmailLogId;

    @PrePersist
    private void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (attemptedAt == null) {
            attemptedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public CustomQuoteRequest getContactRequest() {
        return contactRequest;
    }

    public void setContactRequest(CustomQuoteRequest contactRequest) {
        this.contactRequest = contactRequest;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public String getAttachmentName() {
        return attachmentName;
    }

    public void setAttachmentName(String attachmentName) {
        this.attachmentName = attachmentName;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public OffsetDateTime getAttemptedAt() {
        return attemptedAt;
    }

    public void setAttemptedAt(OffsetDateTime attemptedAt) {
        this.attemptedAt = attemptedAt;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(OffsetDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public UUID getResentFromEmailLogId() {
        return resentFromEmailLogId;
    }

    public void setResentFromEmailLogId(UUID resentFromEmailLogId) {
        this.resentFromEmailLogId = resentFromEmailLogId;
    }
}
