package com.printcalculator.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class AdminContactRequestDetailDto {
    private UUID id;
    private String requestType;
    private String customerType;
    private String email;
    private String phone;
    private String name;
    private String companyName;
    private String contactPerson;
    private String message;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<AdminContactRequestAttachmentDto> attachments;
    private List<AdminEmailLogDto> emailLogs;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getRequestType() {
        return requestType;
    }

    public void setRequestType(String requestType) {
        this.requestType = requestType;
    }

    public String getCustomerType() {
        return customerType;
    }

    public void setCustomerType(String customerType) {
        this.customerType = customerType;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getContactPerson() {
        return contactPerson;
    }

    public void setContactPerson(String contactPerson) {
        this.contactPerson = contactPerson;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<AdminContactRequestAttachmentDto> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<AdminContactRequestAttachmentDto> attachments) {
        this.attachments = attachments;
    }

    public List<AdminEmailLogDto> getEmailLogs() {
        return emailLogs;
    }

    public void setEmailLogs(List<AdminEmailLogDto> emailLogs) {
        this.emailLogs = emailLogs;
    }
}
