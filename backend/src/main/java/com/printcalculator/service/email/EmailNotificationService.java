package com.printcalculator.service.email;

import java.util.Map;

public interface EmailNotificationService {

    /**
     * Sends an HTML email using a Thymeleaf template.
     *
     * @param to           The recipient email address.
     * @param subject      The subject of the email.
     * @param templateName The name of the Thymeleaf template (e.g., "order-confirmation").
     * @param contextData  The data to populate the template with.
     */
    EmailSendResult sendEmail(String to, String subject, String templateName, Map<String, Object> contextData);

    /**
     * Sends an HTML email using a Thymeleaf template, with an optional attachment.
     *
     * @param to             The recipient email address.
     * @param subject        The subject of the email.
     * @param templateName   The name of the Thymeleaf template (e.g., "order-confirmation").
     * @param contextData    The data to populate the template with.
     * @param attachmentName The name for the attachment file.
     * @param attachmentData The raw bytes of the attachment.
     */
    EmailSendResult sendEmailWithAttachment(String to, String subject, String templateName, Map<String, Object> contextData, String attachmentName, byte[] attachmentData);

}
