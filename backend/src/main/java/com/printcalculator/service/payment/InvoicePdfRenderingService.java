package com.printcalculator.service.payment;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.svgsupport.BatikSVGDrawer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.util.stream.Collectors;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.math.BigDecimal;
import java.math.RoundingMode;

import com.printcalculator.entity.Order;
import com.printcalculator.entity.OrderItem;
import com.printcalculator.entity.Payment;

@Service
public class InvoicePdfRenderingService {

    private final TemplateEngine thymeleafTemplateEngine;

    public InvoicePdfRenderingService(TemplateEngine thymeleafTemplateEngine) {
        this.thymeleafTemplateEngine = thymeleafTemplateEngine;
    }

    public byte[] generateInvoicePdfBytesFromTemplate(Map<String, Object> invoiceTemplateVariables, String qrBillSvg) {
        try {
            Context thymeleafContextWithInvoiceData = new Context(InvoiceLanguage.resolve((String) invoiceTemplateVariables.get("language")).locale());
            thymeleafContextWithInvoiceData.setVariables(invoiceTemplateVariables);
            thymeleafContextWithInvoiceData.setVariable("qrBillSvg", qrBillSvg);

            String renderedInvoiceHtml = thymeleafTemplateEngine.process("invoice", thymeleafContextWithInvoiceData);

            String classpathBaseUrlForHtmlResources = new ClassPathResource("templates/").getURL().toExternalForm();

            ByteArrayOutputStream generatedPdfByteArrayOutputStream = new ByteArrayOutputStream();

            PdfRendererBuilder openHtmlToPdfRendererBuilder = new PdfRendererBuilder();
            openHtmlToPdfRendererBuilder.useFastMode();
            openHtmlToPdfRendererBuilder.useSVGDrawer(new BatikSVGDrawer());
            openHtmlToPdfRendererBuilder.withHtmlContent(renderedInvoiceHtml, classpathBaseUrlForHtmlResources);
            openHtmlToPdfRendererBuilder.toStream(generatedPdfByteArrayOutputStream);
            openHtmlToPdfRendererBuilder.run();

            return generatedPdfByteArrayOutputStream.toByteArray();
        } catch (Exception pdfGenerationException) {
            throw new IllegalStateException("PDF invoice generation failed", pdfGenerationException);
        }
    }

    public byte[] generateDocumentPdf(Order order, List<OrderItem> items, boolean isConfirmation, QrBillService qrBillService, Payment payment) {
        Map<String, Object> vars = new HashMap<>();
        InvoiceLanguage language = InvoiceLanguage.resolve(order.getPreferredLanguage());
        vars.put("language", language.code());
        vars.put("labels", language.labels());
        vars.put("isConfirmation", isConfirmation);
        vars.put("sellerDisplayName", "3D Fab Küng Caletti");
        vars.put("sellerAddressLine1", "Joe Küng · Matteo Caletti");
        vars.put("sellerAddressLine2", language.text("location"));
        vars.put("sellerEmail", "info@3dfab.ch");

        String displayOrderNumber = order.getOrderNumber() != null && !order.getOrderNumber().isBlank()
            ? order.getOrderNumber()
            : order.getId().toString();

        vars.put("invoiceNumber", "INV-" + displayOrderNumber.toUpperCase());
        vars.put("invoiceDate", order.getCreatedAt().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        vars.put("dueDate", order.getCreatedAt().plusDays(30).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));

        String buyerName = "BUSINESS".equals(order.getBillingCustomerType())
            ? order.getBillingCompanyName()
            : order.getBillingFirstName() + " " + order.getBillingLastName();
        vars.put("buyerDisplayName", buyerName);
        vars.put("buyerAddressLine1", order.getBillingAddressLine1());
        vars.put("buyerAddressLine2", order.getBillingZip() + " " + order.getBillingCity() + ", " + order.getBillingCountryCode());

        // Setup Shipping Info
        if (order.getShippingAddressLine1() != null && !order.getShippingAddressLine1().isBlank()) {
            String shippingName = order.getShippingCompanyName() != null && !order.getShippingCompanyName().isBlank()
                ? order.getShippingCompanyName()
                : order.getShippingFirstName() + " " + order.getShippingLastName();
            vars.put("shippingDisplayName", shippingName);
            vars.put("shippingAddressLine1", order.getShippingAddressLine1());
            vars.put("shippingAddressLine2", order.getShippingZip() + " " + order.getShippingCity() + ", " + order.getShippingCountryCode());
        }

        List<Map<String, Object>> invoiceLineItems = items.stream()
                .map(item -> toInvoiceLineItem(item, language))
                .collect(Collectors.toList());

        if (order.getCadTotalChf() != null && order.getCadTotalChf().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal cadHours = order.getCadHours() != null ? order.getCadHours() : BigDecimal.ZERO;
            BigDecimal cadHourlyRate = order.getCadHourlyRateChf() != null ? order.getCadHourlyRateChf() : BigDecimal.ZERO;
            Map<String, Object> cadLine = new HashMap<>();
            cadLine.put("description", language.text("cad"));
            cadLine.put("quantity", formatCadHours(cadHours) + " h");
            cadLine.put("unitPriceFormatted", money(cadHourlyRate));
            cadLine.put("lineTotalFormatted", money(order.getCadTotalChf()));
            invoiceLineItems.add(cadLine);
        }

        Map<String, Object> setupLine = new HashMap<>();
        setupLine.put("description", language.text("setup"));
        setupLine.put("quantity", 1);
        setupLine.put("unitPriceFormatted", money(order.getSetupCostChf()));
        setupLine.put("lineTotalFormatted", money(order.getSetupCostChf()));
        invoiceLineItems.add(setupLine);

        Map<String, Object> shippingLine = new HashMap<>();
        shippingLine.put("description", language.text("delivery"));
        shippingLine.put("quantity", 1);
        shippingLine.put("unitPriceFormatted", money(order.getShippingCostChf()));
        shippingLine.put("lineTotalFormatted", money(order.getShippingCostChf()));
        invoiceLineItems.add(shippingLine);

        vars.put("invoiceLineItems", invoiceLineItems);
        vars.put("subtotalFormatted", money(order.getSubtotalChf()));
        vars.put("grandTotalFormatted", money(order.getTotalChf()));
        vars.put("paymentTermsText", isConfirmation ? language.text("terms") : language.text("thanks"));

        String paymentMethodText = language.text("defaultPayment");
        if (payment != null && payment.getMethod() != null) {
            paymentMethodText = switch (payment.getMethod().toUpperCase()) {
                case "TWINT" -> "TWINT";
                case "BANK_TRANSFER", "BONIFICO" -> language.text("transfer");
                case "QR_BILL", "QR" -> "QR Bill";
                case "CASH" -> language.text("cash");
                default -> payment.getMethod();
            };
        }
        vars.put("paymentMethodText", paymentMethodText);

        String qrBillSvg = null;
        if (isConfirmation) {
            qrBillSvg = new String(qrBillService.generateQrBillSvg(order), java.nio.charset.StandardCharsets.UTF_8);

            if (qrBillSvg.contains("<?xml")) {
                int svgStartIndex = qrBillSvg.indexOf("<svg");
                if (svgStartIndex != -1) {
                    qrBillSvg = qrBillSvg.substring(svgStartIndex);
                }
            }
        }

        return generateInvoicePdfBytesFromTemplate(vars, qrBillSvg);
    }

    private String money(BigDecimal amount) {
        java.text.DecimalFormat format = new java.text.DecimalFormat("#,##0.00",
                java.text.DecimalFormatSymbols.getInstance(Locale.forLanguageTag("de-CH")));
        return "CHF " + format.format(amount != null ? amount : BigDecimal.ZERO).replace('’', '\'');
    }

    private String formatCadHours(BigDecimal hours) {
        return hours.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private Map<String, Object> toInvoiceLineItem(OrderItem item, InvoiceLanguage language) {
        Map<String, Object> line = new HashMap<>();
        line.put("description", buildLineDescription(item, language));
        line.put("printSettings", printSettings(item, language));
        line.put("quantity", item.getQuantity());
        line.put("unitPriceFormatted", money(item.getUnitPriceChf()));
        line.put("lineTotalFormatted", money(item.getLineTotalChf()));
        return line;
    }

    private String printSettings(OrderItem item, InvoiceLanguage language) {
        if ("SHOP_PRODUCT".equalsIgnoreCase(item.getItemType())) return null;
        java.util.List<String> details = new java.util.ArrayList<>();
        addSetting(details, language.text("material"), item.getMaterialCode(), "");
        addSetting(details, language.text("color"), language.setting("color", item.getColorCode()), "");
        addSetting(details, language.text("quality"), language.setting("quality", item.getQuality()), "");
        addSetting(details, language.text("nozzle"), item.getNozzleDiameterMm(), " mm");
        addSetting(details, language.text("layer"), item.getLayerHeightMm(), " mm");
        addSetting(details, language.text("infill"), item.getInfillPercent(), "%");
        addSetting(details, language.text("pattern"), language.setting("pattern", item.getInfillPattern()), "");
        addSetting(details, language.text("supports"), item.getSupportsEnabled() == null ? null : item.getSupportsEnabled() ? language.text("yes") : language.text("no"), "");
        addSetting(details, language.text("split"), item.getRequiresSplitPrinting() == null ? null : item.getRequiresSplitPrinting() ? language.text("yes") : language.text("no"), "");
        return String.join(" · ", details);
    }

    private void addSetting(java.util.List<String> details, String label, Object value, String unit) {
        if (value != null && !value.toString().isBlank()) details.add(label + ": " + value + unit);
    }

    private String buildLineDescription(OrderItem item, InvoiceLanguage language) {
        if (item == null) {
            return language.text("item");
        }

        if ("SHOP_PRODUCT".equalsIgnoreCase(item.getItemType())) {
            String productName = firstNonBlank(
                    item.getDisplayName(),
                    item.getShopProductName(),
                    item.getOriginalFilename(),
                    language.text("shop")
            );
            String variantLabel = firstNonBlank(item.getShopVariantLabel(), item.getShopVariantColorName(), null);
            return variantLabel != null ? productName + " - " + variantLabel : productName;
        }

        String fileName = firstNonBlank(item.getDisplayName(), item.getOriginalFilename(), language.text("file"));
        return language.text("print") + ": " + fileName;
    }

    private String firstNonBlank(String... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
