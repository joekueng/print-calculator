package com.printcalculator.event.listener;

import com.printcalculator.entity.Order;
import com.printcalculator.entity.OrderItem;
import com.printcalculator.entity.EmailLog;
import com.printcalculator.entity.Payment;
import com.printcalculator.event.OrderCreatedEvent;
import com.printcalculator.event.OrderShippedEvent;
import com.printcalculator.event.PaymentConfirmedEvent;
import com.printcalculator.event.PaymentReportedEvent;
import com.printcalculator.repository.OrderItemRepository;
import com.printcalculator.repository.OrderRepository;
import com.printcalculator.repository.PaymentRepository;
import com.printcalculator.service.email.EmailAuditService;
import com.printcalculator.service.email.EmailSendResult;
import com.printcalculator.service.payment.InvoicePdfRenderingService;
import com.printcalculator.service.payment.QrBillService;
import com.printcalculator.service.storage.StorageService;
import com.printcalculator.service.email.EmailNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEmailListener {

    private static final String DEFAULT_LANGUAGE = "it";

    private final EmailNotificationService emailNotificationService;
    private final InvoicePdfRenderingService invoicePdfRenderingService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final QrBillService qrBillService;
    private final StorageService storageService;
    private final EmailAuditService emailAuditService;

    @Value("${app.mail.admin.enabled:true}")
    private boolean adminMailEnabled;

    @Value("${app.mail.admin.address:}")
    private String adminMailAddress;

    @Value("${app.frontend.base-url:http://localhost:4200}")
    private String frontendBaseUrl;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreatedEvent(OrderCreatedEvent event) {
        Order order = loadCommittedOrder(event.getOrder());
        log.info("Processing OrderCreatedEvent for order id: {}", order.getId());

        try {
            sendCustomerConfirmationEmail(order);

            sendAdminNotificationEmail(order);
        } catch (Exception e) {
            log.error("Failed to process email notifications for order id: {}", order.getId(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentReportedEvent(PaymentReportedEvent event) {
        Order order = loadCommittedOrder(event.getOrder());
        log.info("Processing PaymentReportedEvent for order id: {}", order.getId());

        try {
            sendPaymentReportedEmail(order);
        } catch (Exception e) {
            log.error("Failed to send payment reported email for order id: {}", order.getId(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentConfirmedEvent(PaymentConfirmedEvent event) {
        Order order = loadCommittedOrder(event.getOrder());
        Payment payment = event.getPayment();
        log.info("Processing PaymentConfirmedEvent for order id: {}", order.getId());

        try {
            sendPaidInvoiceEmail(order, payment);
        } catch (Exception e) {
            log.error("Failed to send paid invoice email for order id: {}", order.getId(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderShippedEvent(OrderShippedEvent event) {
        Order order = loadCommittedOrder(event.getOrder());
        log.info("Processing OrderShippedEvent for order id: {}", order.getId());

        try {
            sendOrderShippedEmail(order);
        } catch (Exception e) {
            log.error("Failed to send order shipped email for order id: {}", order.getId(), e);
        }
    }

    private void sendCustomerConfirmationEmail(Order order) {
        sendCustomerConfirmationEmail(order, EmailAuditService.ORIGIN_SYSTEM, null);
    }

    private Order loadCommittedOrder(Order eventOrder) {
        if (eventOrder == null || eventOrder.getId() == null) {
            return eventOrder;
        }
        return orderRepository.findForEmailById(eventOrder.getId()).orElse(eventOrder);
    }

    public EmailLog sendCustomerConfirmationEmail(Order order, String origin, UUID resentFromEmailLogId) {
        String language = resolveLanguage(order.getPreferredLanguage());
        String orderNumber = getDisplayOrderNumber(order);

        Map<String, Object> templateData = buildBaseTemplateData(order, language);
        String subject = applyOrderConfirmationTexts(templateData, language, orderNumber);
        byte[] confirmationPdf = loadOrGenerateConfirmationPdf(order);
        String attachmentName = buildConfirmationAttachmentName(language, orderNumber);

        EmailSendResult result = emailNotificationService.sendEmailWithAttachment(
                resolveCustomerEmail(order),
                subject,
                "order-confirmation",
                templateData,
                attachmentName,
                confirmationPdf
        );

        return emailAuditService.recordOrderEmail(
                order,
                EmailAuditService.EVENT_ORDER_CONFIRMATION_CUSTOMER,
                origin,
                resolveCustomerEmail(order),
                subject,
                "order-confirmation",
                attachmentName,
                result,
                resentFromEmailLogId
        );
    }

    private void sendPaymentReportedEmail(Order order) {
        sendPaymentReportedEmail(order, EmailAuditService.ORIGIN_SYSTEM, null);
    }

    public EmailLog sendPaymentReportedEmail(Order order, String origin, UUID resentFromEmailLogId) {
        String language = resolveLanguage(order.getPreferredLanguage());
        String orderNumber = getDisplayOrderNumber(order);

        Map<String, Object> templateData = buildBaseTemplateData(order, language);
        String subject = applyPaymentReportedTexts(templateData, language, orderNumber);

        EmailSendResult result = emailNotificationService.sendEmail(
                resolveCustomerEmail(order),
                subject,
                "payment-reported",
                templateData
        );

        return emailAuditService.recordOrderEmail(
                order,
                EmailAuditService.EVENT_PAYMENT_REPORTED_CUSTOMER,
                origin,
                resolveCustomerEmail(order),
                subject,
                "payment-reported",
                null,
                result,
                resentFromEmailLogId
        );
    }

    private void sendPaidInvoiceEmail(Order order, Payment payment) {
        sendPaidInvoiceEmail(order, payment, EmailAuditService.ORIGIN_SYSTEM, null);
    }

    public EmailLog sendPaidInvoiceEmail(Order order, Payment payment, String origin, UUID resentFromEmailLogId) {
        String language = resolveLanguage(order.getPreferredLanguage());
        String orderNumber = getDisplayOrderNumber(order);

        Map<String, Object> templateData = buildBaseTemplateData(order, language);
        String subject = applyPaymentConfirmedTexts(templateData, language, orderNumber);

        byte[] pdf = null;
        try {
            List<OrderItem> items = orderItemRepository.findByOrder_Id(order.getId());
            pdf = invoicePdfRenderingService.generateDocumentPdf(order, items, false, qrBillService, payment);
        } catch (Exception e) {
            log.error("Failed to generate PDF for paid invoice email: {}", e.getMessage(), e);
        }

        String attachmentName = buildPaidInvoiceAttachmentName(language, orderNumber);
        EmailSendResult result = emailNotificationService.sendEmailWithAttachment(
                resolveCustomerEmail(order),
                subject,
                "payment-confirmed",
                templateData,
                attachmentName,
                pdf
        );

        return emailAuditService.recordOrderEmail(
                order,
                EmailAuditService.EVENT_PAYMENT_CONFIRMED_CUSTOMER,
                origin,
                resolveCustomerEmail(order),
                subject,
                "payment-confirmed",
                attachmentName,
                result,
                resentFromEmailLogId
        );
    }

    private void sendOrderShippedEmail(Order order) {
        sendOrderShippedEmail(order, EmailAuditService.ORIGIN_SYSTEM, null);
    }

    public EmailLog sendOrderShippedEmail(Order order, String origin, UUID resentFromEmailLogId) {
        String language = resolveLanguage(order.getPreferredLanguage());
        String orderNumber = getDisplayOrderNumber(order);

        Map<String, Object> templateData = buildBaseTemplateData(order, language);
        String subject = applyOrderShippedTexts(templateData, language, orderNumber);

        EmailSendResult result = emailNotificationService.sendEmail(
                resolveCustomerEmail(order),
                subject,
                "order-shipped",
                templateData
        );

        return emailAuditService.recordOrderEmail(
                order,
                EmailAuditService.EVENT_ORDER_SHIPPED_CUSTOMER,
                origin,
                resolveCustomerEmail(order),
                subject,
                "order-shipped",
                null,
                result,
                resentFromEmailLogId
        );
    }

    private void sendAdminNotificationEmail(Order order) {
        sendAdminNotificationEmail(order, EmailAuditService.ORIGIN_SYSTEM, null);
    }

    public EmailLog sendAdminNotificationEmail(Order order, String origin, UUID resentFromEmailLogId) {
        if (!adminMailEnabled) {
            return recordSkippedOrderEmail(
                    order,
                    EmailAuditService.EVENT_ORDER_NOTIFICATION_ADMIN,
                    origin,
                    adminMailAddress,
                    null,
                    "order-confirmation",
                    "Admin order notification disabled.",
                    resentFromEmailLogId
            );
        }
        if (adminMailAddress == null || adminMailAddress.isBlank()) {
            return recordSkippedOrderEmail(
                    order,
                    EmailAuditService.EVENT_ORDER_NOTIFICATION_ADMIN,
                    origin,
                    adminMailAddress,
                    null,
                    "order-confirmation",
                    "Admin order notification address is missing.",
                    resentFromEmailLogId
            );
        }

        String orderNumber = getDisplayOrderNumber(order);
        Map<String, Object> templateData = buildBaseTemplateData(order, DEFAULT_LANGUAGE);
        templateData.put("customerName", buildCustomerFullName(order));

        templateData.put("emailTitle", "Nuovo ordine ricevuto");
        templateData.put("headlineText", "Nuovo ordine #" + orderNumber);
        templateData.put("greetingText", "Ciao team,");
        templateData.put("introText", "Un nuovo ordine e' stato creato dal cliente.");
        templateData.put("detailsTitleText", "Dettagli ordine");
        templateData.put("labelOrderNumber", "Numero ordine");
        templateData.put("labelDate", "Data");
        templateData.put("labelTotal", "Totale");
        templateData.put("orderDetailsCtaText", "Apri dettaglio ordine");
        templateData.put("attachmentHintText", "La conferma cliente e il QR bill sono stati salvati nella cartella documenti dell'ordine.");
        templateData.put("supportText", "Controlla i dettagli e procedi con la gestione operativa.");
        templateData.put("footerText", "Notifica automatica sistema ordini.");
        String subject = "Nuovo Ordine Ricevuto #" + orderNumber + " - " + buildCustomerFullName(order);

        EmailSendResult result = emailNotificationService.sendEmail(
                adminMailAddress,
                subject,
                "order-confirmation",
                templateData
        );

        return emailAuditService.recordOrderEmail(
                order,
                EmailAuditService.EVENT_ORDER_NOTIFICATION_ADMIN,
                origin,
                adminMailAddress,
                subject,
                "order-confirmation",
                null,
                result,
                resentFromEmailLogId
        );
    }

    public EmailLog resendOrderEmail(Order order, EmailLog sourceLog) {
        String eventType = sourceLog.getEventType();
        UUID resentFromEmailLogId = sourceLog.getId();

        return switch (eventType) {
            case EmailAuditService.EVENT_ORDER_CONFIRMATION_CUSTOMER ->
                    sendCustomerConfirmationEmail(order, EmailAuditService.ORIGIN_ADMIN, resentFromEmailLogId);
            case EmailAuditService.EVENT_ORDER_NOTIFICATION_ADMIN ->
                    sendAdminNotificationEmail(order, EmailAuditService.ORIGIN_ADMIN, resentFromEmailLogId);
            case EmailAuditService.EVENT_PAYMENT_REPORTED_CUSTOMER ->
                    sendPaymentReportedEmail(order, EmailAuditService.ORIGIN_ADMIN, resentFromEmailLogId);
            case EmailAuditService.EVENT_PAYMENT_CONFIRMED_CUSTOMER ->
                    paymentRepository.findByOrder_Id(order.getId())
                            .map(payment -> sendPaidInvoiceEmail(order, payment, EmailAuditService.ORIGIN_ADMIN, resentFromEmailLogId))
                            .orElseGet(() -> recordSkippedOrderEmail(
                                    order,
                                    EmailAuditService.EVENT_PAYMENT_CONFIRMED_CUSTOMER,
                                    EmailAuditService.ORIGIN_ADMIN,
                                    resolveCustomerEmail(order),
                                    sourceLog.getSubject(),
                                    "payment-confirmed",
                                    "Cannot resend paid invoice email because no payment record is available.",
                                    resentFromEmailLogId
                            ));
            case EmailAuditService.EVENT_ORDER_SHIPPED_CUSTOMER ->
                    sendOrderShippedEmail(order, EmailAuditService.ORIGIN_ADMIN, resentFromEmailLogId);
            default -> recordSkippedOrderEmail(
                    order,
                    eventType,
                    EmailAuditService.ORIGIN_ADMIN,
                    sourceLog.getRecipient(),
                    sourceLog.getSubject(),
                    sourceLog.getTemplateName(),
                    "Unsupported order email type: " + eventType,
                    resentFromEmailLogId
            );
        };
    }

    private EmailLog recordSkippedOrderEmail(Order order,
                                             String eventType,
                                             String origin,
                                             String recipient,
                                             String subject,
                                             String templateName,
                                             String reason,
                                             UUID resentFromEmailLogId) {
        return emailAuditService.recordOrderEmail(
                order,
                eventType,
                origin,
                recipient,
                subject,
                templateName,
                null,
                EmailSendResult.skipped(OffsetDateTime.now(), reason),
                resentFromEmailLogId
        );
    }

    private Map<String, Object> buildBaseTemplateData(Order order, String language) {
        Locale locale = localeForLanguage(language);
        NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(locale);
        currencyFormatter.setCurrency(Currency.getInstance("CHF"));

        Map<String, Object> templateData = new HashMap<>();
        templateData.put("customerName", buildCustomerFirstName(order, language));
        templateData.put("orderId", order.getId());
        templateData.put("orderNumber", getDisplayOrderNumber(order));
        templateData.put("orderDetailsUrl", buildOrderDetailsUrl(order, language));
        templateData.put(
                "orderDate",
                order.getCreatedAt().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale))
        );
        templateData.put("totalCost", currencyFormatter.format(order.getTotalChf()));
        templateData.put("logoUrl", buildLogoUrl());
        templateData.put("currentYear", Year.now().getValue());
        return templateData;
    }

    private String buildLogoUrl() {
        return frontendBaseUrl.replaceAll("/+$", "") + "/assets/images/SVG/logo-giallo-spesso.svg";
    }

    private String applyOrderConfirmationTexts(Map<String, Object> templateData, String language, String orderNumber) {
        return switch (language) {
            case "en" -> {
                templateData.put("emailTitle", "Order Confirmation");
                templateData.put("headlineText", "Thank you for your order #" + orderNumber);
                templateData.put("greetingText", "Hi " + templateData.get("customerName") + ",");
                templateData.put("introText", "We received your order and started processing it.");
                templateData.put("detailsTitleText", "Order details");
                templateData.put("labelOrderNumber", "Order number");
                templateData.put("labelDate", "Date");
                templateData.put("labelTotal", "Total");
                templateData.put("orderDetailsCtaText", "View order status");
                templateData.put("attachmentHintText", "Attached you can find the order confirmation PDF with the QR bill.");
                templateData.put("supportText", "If you have questions, reply to this email and we will help you.");
                templateData.put("footerText", "Automated message from 3D-Fab.");
                yield "Order Confirmation #" + orderNumber + " - 3D-Fab";
            }
            case "de" -> {
                templateData.put("emailTitle", "Bestellbestaetigung");
                templateData.put("headlineText", "Danke fuer Ihre Bestellung #" + orderNumber);
                templateData.put("greetingText", "Hallo " + templateData.get("customerName") + ",");
                templateData.put("introText", "Wir haben Ihre Bestellung erhalten und mit der Bearbeitung begonnen.");
                templateData.put("detailsTitleText", "Bestelldetails");
                templateData.put("labelOrderNumber", "Bestellnummer");
                templateData.put("labelDate", "Datum");
                templateData.put("labelTotal", "Gesamtbetrag");
                templateData.put("orderDetailsCtaText", "Bestellstatus ansehen");
                templateData.put("attachmentHintText", "Im Anhang finden Sie die Bestellbestaetigung mit QR-Rechnung.");
                templateData.put("supportText", "Bei Fragen antworten Sie einfach auf diese E-Mail.");
                templateData.put("footerText", "Automatische Nachricht von 3D-Fab.");
                yield "Bestellbestaetigung #" + orderNumber + " - 3D-Fab";
            }
            case "fr" -> {
                templateData.put("emailTitle", "Confirmation de commande");
                templateData.put("headlineText", "Merci pour votre commande #" + orderNumber);
                templateData.put("greetingText", "Bonjour " + templateData.get("customerName") + ",");
                templateData.put("introText", "Nous avons recu votre commande et commence son traitement.");
                templateData.put("detailsTitleText", "Details de commande");
                templateData.put("labelOrderNumber", "Numero de commande");
                templateData.put("labelDate", "Date");
                templateData.put("labelTotal", "Total");
                templateData.put("orderDetailsCtaText", "Voir le statut de la commande");
                templateData.put("attachmentHintText", "Vous trouverez en piece jointe la confirmation de commande avec la facture QR.");
                templateData.put("supportText", "Si vous avez des questions, repondez a cet email.");
                templateData.put("footerText", "Message automatique de 3D-Fab.");
                yield "Confirmation de commande #" + orderNumber + " - 3D-Fab";
            }
            default -> {
                templateData.put("emailTitle", "Conferma ordine");
                templateData.put("headlineText", "Grazie per il tuo ordine #" + orderNumber);
                templateData.put("greetingText", "Ciao " + templateData.get("customerName") + ",");
                templateData.put("introText", "Abbiamo ricevuto il tuo ordine e iniziato l'elaborazione.");
                templateData.put("detailsTitleText", "Dettagli ordine");
                templateData.put("labelOrderNumber", "Numero ordine");
                templateData.put("labelDate", "Data");
                templateData.put("labelTotal", "Totale");
                templateData.put("orderDetailsCtaText", "Visualizza stato ordine");
                templateData.put("attachmentHintText", "In allegato trovi la conferma ordine in PDF con QR bill.");
                templateData.put("supportText", "Se hai domande, rispondi a questa email e ti aiutiamo subito.");
                templateData.put("footerText", "Messaggio automatico di 3D-Fab.");
                yield "Conferma Ordine #" + orderNumber + " - 3D-Fab";
            }
        };
    }

    private String applyPaymentReportedTexts(Map<String, Object> templateData, String language, String orderNumber) {
        return switch (language) {
            case "en" -> {
                templateData.put("emailTitle", "Payment Reported");
                templateData.put("headlineText", "Payment reported for order #" + orderNumber);
                templateData.put("greetingText", "Hi " + templateData.get("customerName") + ",");
                templateData.put("introText", "We received your payment report and our team is now verifying it.");
                templateData.put("statusText", "Current status: Payment under verification.");
                templateData.put("orderDetailsCtaText", "Check order status");
                templateData.put("supportText", "You will receive another email as soon as the payment is confirmed.");
                templateData.put("footerText", "Automated message from 3D-Fab.");
                templateData.put("labelOrderNumber", "Order number");
                templateData.put("labelTotal", "Total");
                yield "We are verifying your payment (Order #" + orderNumber + ")";
            }
            case "de" -> {
                templateData.put("emailTitle", "Zahlung gemeldet");
                templateData.put("headlineText", "Zahlung fuer Bestellung #" + orderNumber + " gemeldet");
                templateData.put("greetingText", "Hallo " + templateData.get("customerName") + ",");
                templateData.put("introText", "Wir haben Ihre Zahlungsmitteilung erhalten und pruefen sie aktuell.");
                templateData.put("statusText", "Aktueller Status: Zahlung in Pruefung.");
                templateData.put("orderDetailsCtaText", "Bestellstatus ansehen");
                templateData.put("supportText", "Sobald die Zahlung bestaetigt ist, erhalten Sie eine weitere E-Mail.");
                templateData.put("footerText", "Automatische Nachricht von 3D-Fab.");
                templateData.put("labelOrderNumber", "Bestellnummer");
                templateData.put("labelTotal", "Gesamtbetrag");
                yield "Wir pruefen Ihre Zahlung (Bestellung #" + orderNumber + ")";
            }
            case "fr" -> {
                templateData.put("emailTitle", "Paiement signale");
                templateData.put("headlineText", "Paiement signale pour la commande #" + orderNumber);
                templateData.put("greetingText", "Bonjour " + templateData.get("customerName") + ",");
                templateData.put("introText", "Nous avons recu votre signalement de paiement et nous le verifions.");
                templateData.put("statusText", "Statut actuel: Paiement en verification.");
                templateData.put("orderDetailsCtaText", "Consulter le statut de la commande");
                templateData.put("supportText", "Vous recevrez un nouvel email des que le paiement sera confirme.");
                templateData.put("footerText", "Message automatique de 3D-Fab.");
                templateData.put("labelOrderNumber", "Numero de commande");
                templateData.put("labelTotal", "Total");
                yield "Nous verifions votre paiement (Commande #" + orderNumber + ")";
            }
            default -> {
                templateData.put("emailTitle", "Pagamento segnalato");
                templateData.put("headlineText", "Pagamento segnalato per ordine #" + orderNumber);
                templateData.put("greetingText", "Ciao " + templateData.get("customerName") + ",");
                templateData.put("introText", "Abbiamo ricevuto la tua segnalazione di pagamento e la stiamo verificando.");
                templateData.put("statusText", "Stato attuale: pagamento in verifica.");
                templateData.put("orderDetailsCtaText", "Controlla lo stato ordine");
                templateData.put("supportText", "Riceverai una nuova email non appena il pagamento sara' confermato.");
                templateData.put("footerText", "Messaggio automatico di 3D-Fab.");
                templateData.put("labelOrderNumber", "Numero ordine");
                templateData.put("labelTotal", "Totale");
                yield "Stiamo verificando il tuo pagamento (Ordine #" + orderNumber + ")";
            }
        };
    }

    private String applyPaymentConfirmedTexts(Map<String, Object> templateData, String language, String orderNumber) {
        return switch (language) {
            case "en" -> {
                templateData.put("emailTitle", "Payment Confirmed");
                templateData.put("headlineText", "Payment confirmed for order #" + orderNumber);
                templateData.put("greetingText", "Hi " + templateData.get("customerName") + ",");
                templateData.put("introText", "Your payment has been confirmed and the order moved into production.");
                templateData.put("statusText", "Current status: In production.");
                templateData.put("attachmentHintText", "The paid invoice PDF is attached to this email.");
                templateData.put("orderDetailsCtaText", "View order status");
                templateData.put("supportText", "We will notify you again when the shipment is ready.");
                templateData.put("footerText", "Automated message from 3D-Fab.");
                templateData.put("labelOrderNumber", "Order number");
                templateData.put("labelTotal", "Total");
                yield "Payment confirmed (Order #" + orderNumber + ") - 3D-Fab";
            }
            case "de" -> {
                templateData.put("emailTitle", "Zahlung bestaetigt");
                templateData.put("headlineText", "Zahlung fuer Bestellung #" + orderNumber + " bestaetigt");
                templateData.put("greetingText", "Hallo " + templateData.get("customerName") + ",");
                templateData.put("introText", "Ihre Zahlung wurde bestaetigt und die Bestellung ist jetzt in Produktion.");
                templateData.put("statusText", "Aktueller Status: In Produktion.");
                templateData.put("attachmentHintText", "Die bezahlte Rechnung als PDF ist dieser E-Mail beigefuegt.");
                templateData.put("orderDetailsCtaText", "Bestellstatus ansehen");
                templateData.put("supportText", "Wir informieren Sie erneut, sobald der Versand bereit ist.");
                templateData.put("footerText", "Automatische Nachricht von 3D-Fab.");
                templateData.put("labelOrderNumber", "Bestellnummer");
                templateData.put("labelTotal", "Gesamtbetrag");
                yield "Zahlung bestaetigt (Bestellung #" + orderNumber + ") - 3D-Fab";
            }
            case "fr" -> {
                templateData.put("emailTitle", "Paiement confirme");
                templateData.put("headlineText", "Paiement confirme pour la commande #" + orderNumber);
                templateData.put("greetingText", "Bonjour " + templateData.get("customerName") + ",");
                templateData.put("introText", "Votre paiement est confirme et la commande est passe en production.");
                templateData.put("statusText", "Statut actuel: En production.");
                templateData.put("attachmentHintText", "La facture payee en PDF est jointe a cet email.");
                templateData.put("orderDetailsCtaText", "Voir le statut de la commande");
                templateData.put("supportText", "Nous vous informerons a nouveau des que l'expedition sera prete.");
                templateData.put("footerText", "Message automatique de 3D-Fab.");
                templateData.put("labelOrderNumber", "Numero de commande");
                templateData.put("labelTotal", "Total");
                yield "Paiement confirme (Commande #" + orderNumber + ") - 3D-Fab";
            }
            default -> {
                templateData.put("emailTitle", "Pagamento confermato");
                templateData.put("headlineText", "Pagamento confermato per ordine #" + orderNumber);
                templateData.put("greetingText", "Ciao " + templateData.get("customerName") + ",");
                templateData.put("introText", "Il tuo pagamento e' stato confermato e l'ordine e' entrato in produzione.");
                templateData.put("statusText", "Stato attuale: in produzione.");
                templateData.put("attachmentHintText", "In allegato trovi la fattura saldata in PDF.");
                templateData.put("orderDetailsCtaText", "Visualizza stato ordine");
                templateData.put("supportText", "Ti aggiorneremo di nuovo quando la spedizione sara' pronta.");
                templateData.put("footerText", "Messaggio automatico di 3D-Fab.");
                templateData.put("labelOrderNumber", "Numero ordine");
                templateData.put("labelTotal", "Totale");
                yield "Pagamento confermato (Ordine #" + orderNumber + ") - 3D-Fab";
            }
        };
    }

    private String applyOrderShippedTexts(Map<String, Object> templateData, String language, String orderNumber) {
        return switch (language) {
            case "en" -> {
                templateData.put("emailTitle", "Order Shipped");
                templateData.put("headlineText", "Your order #" + orderNumber + " has been shipped");
                templateData.put("greetingText", "Hi " + templateData.get("customerName") + ",");
                templateData.put("introText", "Good news: your package has left our workshop and is on its way.");
                templateData.put("statusText", "Current status: Shipped.");
                templateData.put("orderDetailsCtaText", "View order status");
                templateData.put("supportText", "If you need assistance, reply to this email.");
                templateData.put("footerText", "Automated message from 3D-Fab.");
                templateData.put("labelOrderNumber", "Order number");
                templateData.put("labelTotal", "Total");
                yield "Your order has been shipped (Order #" + orderNumber + ") - 3D-Fab";
            }
            case "de" -> {
                templateData.put("emailTitle", "Bestellung versandt");
                templateData.put("headlineText", "Ihre Bestellung #" + orderNumber + " wurde versandt");
                templateData.put("greetingText", "Hallo " + templateData.get("customerName") + ",");
                templateData.put("introText", "Gute Nachricht: Ihr Paket hat unsere Werkstatt verlassen und ist unterwegs.");
                templateData.put("statusText", "Aktueller Status: Versandt.");
                templateData.put("orderDetailsCtaText", "Bestellstatus ansehen");
                templateData.put("supportText", "Wenn Sie Hilfe benoetigen, antworten Sie auf diese E-Mail.");
                templateData.put("footerText", "Automatische Nachricht von 3D-Fab.");
                templateData.put("labelOrderNumber", "Bestellnummer");
                templateData.put("labelTotal", "Gesamtbetrag");
                yield "Ihre Bestellung wurde versandt (Bestellung #" + orderNumber + ") - 3D-Fab";
            }
            case "fr" -> {
                templateData.put("emailTitle", "Commande expediee");
                templateData.put("headlineText", "Votre commande #" + orderNumber + " a ete expediee");
                templateData.put("greetingText", "Bonjour " + templateData.get("customerName") + ",");
                templateData.put("introText", "Bonne nouvelle: votre colis a quitte notre atelier et est en route.");
                templateData.put("statusText", "Statut actuel: Expediee.");
                templateData.put("orderDetailsCtaText", "Voir le statut de la commande");
                templateData.put("supportText", "Si vous avez besoin d'aide, repondez a cet email.");
                templateData.put("footerText", "Message automatique de 3D-Fab.");
                templateData.put("labelOrderNumber", "Numero de commande");
                templateData.put("labelTotal", "Total");
                yield "Votre commande a ete expediee (Commande #" + orderNumber + ") - 3D-Fab";
            }
            default -> {
                templateData.put("emailTitle", "Ordine spedito");
                templateData.put("headlineText", "Il tuo ordine #" + orderNumber + " e' stato spedito");
                templateData.put("greetingText", "Ciao " + templateData.get("customerName") + ",");
                templateData.put("introText", "Buone notizie: il tuo pacco e' partito dal nostro laboratorio ed e' in viaggio.");
                templateData.put("statusText", "Stato attuale: spedito.");
                templateData.put("orderDetailsCtaText", "Visualizza stato ordine");
                templateData.put("supportText", "Se hai bisogno di assistenza, rispondi a questa email.");
                templateData.put("footerText", "Messaggio automatico di 3D-Fab.");
                templateData.put("labelOrderNumber", "Numero ordine");
                templateData.put("labelTotal", "Totale");
                yield "Il tuo ordine e' stato spedito (Ordine #" + orderNumber + ") - 3D-Fab";
            }
        };
    }

    private String getDisplayOrderNumber(Order order) {
        String orderNumber = order.getOrderNumber();
        if (orderNumber != null && !orderNumber.isBlank()) {
            return orderNumber;
        }
        return order.getId() != null ? order.getId().toString() : "unknown";
    }

    private String buildOrderDetailsUrl(Order order, String language) {
        String baseUrl = frontendBaseUrl == null ? "" : frontendBaseUrl.replaceAll("/+$", "");
        return baseUrl + "/" + language + "/co/" + order.getId();
    }

    private String buildConfirmationAttachmentName(String language, String orderNumber) {
        return switch (language) {
            case "en" -> "Order-Confirmation-" + orderNumber + ".pdf";
            case "de" -> "Bestellbestaetigung-" + orderNumber + ".pdf";
            case "fr" -> "Confirmation-Commande-" + orderNumber + ".pdf";
            default -> "Conferma-Ordine-" + orderNumber + ".pdf";
        };
    }

    private String buildPaidInvoiceAttachmentName(String language, String orderNumber) {
        return switch (language) {
            case "en" -> "Paid-Invoice-" + orderNumber + ".pdf";
            case "de" -> "Bezahlte-Rechnung-" + orderNumber + ".pdf";
            case "fr" -> "Facture-Payee-" + orderNumber + ".pdf";
            default -> "Fattura-Pagata-" + orderNumber + ".pdf";
        };
    }

    private byte[] loadOrGenerateConfirmationPdf(Order order) {
        byte[] stored = loadStoredConfirmationPdf(order);
        if (stored != null) {
            return stored;
        }

        try {
            List<OrderItem> items = orderItemRepository.findByOrder_Id(order.getId());
            return invoicePdfRenderingService.generateDocumentPdf(order, items, true, qrBillService, null);
        } catch (Exception e) {
            log.error("Failed to generate fallback confirmation PDF for order id: {}", order.getId(), e);
            return null;
        }
    }

    private byte[] loadStoredConfirmationPdf(Order order) {
        String relativePath = buildConfirmationPdfRelativePath(order);
        try {
            return storageService.loadAsResource(Paths.get(relativePath)).getInputStream().readAllBytes();
        } catch (Exception e) {
            log.warn("Confirmation PDF not found for order id {} at {}", order.getId(), relativePath);
            return null;
        }
    }

    private String buildConfirmationPdfRelativePath(Order order) {
        return "orders/" + order.getId() + "/documents/confirmation-" + getDisplayOrderNumber(order) + ".pdf";
    }

    private String buildCustomerFirstName(Order order, String language) {
        if (order.getCustomer() != null && order.getCustomer().getFirstName() != null && !order.getCustomer().getFirstName().isBlank()) {
            return order.getCustomer().getFirstName();
        }
        if (order.getBillingFirstName() != null && !order.getBillingFirstName().isBlank()) {
            return order.getBillingFirstName();
        }
        return switch (language) {
            case "en" -> "Customer";
            case "de" -> "Kunde";
            case "fr" -> "Client";
            default -> "Cliente";
        };
    }

    private String buildCustomerFullName(Order order) {
        String firstName = order.getCustomer() != null ? order.getCustomer().getFirstName() : null;
        String lastName = order.getCustomer() != null ? order.getCustomer().getLastName() : null;
        if (firstName != null && !firstName.isBlank() && lastName != null && !lastName.isBlank()) {
            return firstName + " " + lastName;
        }
        if (order.getBillingFirstName() != null && !order.getBillingFirstName().isBlank()
                && order.getBillingLastName() != null && !order.getBillingLastName().isBlank()) {
            return order.getBillingFirstName() + " " + order.getBillingLastName();
        }
        return "Cliente";
    }

    private String resolveCustomerEmail(Order order) {
        if (order.getCustomer() != null && order.getCustomer().getEmail() != null && !order.getCustomer().getEmail().isBlank()) {
            return order.getCustomer().getEmail();
        }
        return order.getCustomerEmail();
    }

    private Locale localeForLanguage(String language) {
        return switch (language) {
            case "en" -> Locale.ENGLISH;
            case "de" -> Locale.GERMAN;
            case "fr" -> Locale.FRENCH;
            default -> Locale.ITALIAN;
        };
    }

    private String resolveLanguage(String language) {
        if (language == null || language.isBlank()) {
            return DEFAULT_LANGUAGE;
        }

        String normalized = language.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 2) {
            normalized = normalized.substring(0, 2);
        }

        return switch (normalized) {
            case "it", "en", "de", "fr" -> normalized;
            default -> DEFAULT_LANGUAGE;
        };
    }
}
