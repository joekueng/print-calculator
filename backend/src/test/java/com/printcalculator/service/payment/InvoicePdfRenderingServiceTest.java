package com.printcalculator.service.payment;

import com.printcalculator.entity.Order;
import com.printcalculator.entity.OrderItem;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InvoicePdfRenderingServiceTest {

    @Test
    void generateDocumentPdf_shouldDescribeShopItemsWithProductAndVariant() throws Exception {
        CapturingInvoicePdfRenderingService service = new CapturingInvoicePdfRenderingService();
        QrBillService qrBillService = mock(QrBillService.class);
        when(qrBillService.generateQrBillSvg(org.mockito.ArgumentMatchers.any(Order.class)))
                .thenReturn("<svg/>".getBytes(StandardCharsets.UTF_8));

        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCreatedAt(OffsetDateTime.parse("2026-03-10T10:15:30+01:00"));
        order.setBillingCustomerType("PRIVATE");
        order.setBillingFirstName("Joe");
        order.setBillingLastName("Buyer");
        order.setBillingAddressLine1("Via Test 1");
        order.setBillingZip("6900");
        order.setBillingCity("Lugano");
        order.setBillingCountryCode("CH");
        order.setSetupCostChf(BigDecimal.ZERO);
        order.setShippingCostChf(new BigDecimal("2.00"));
        order.setSubtotalChf(new BigDecimal("36.80"));
        order.setTotalChf(new BigDecimal("38.80"));
        order.setCadTotalChf(BigDecimal.ZERO);

        OrderItem shopItem = new OrderItem();
        shopItem.setItemType("SHOP_PRODUCT");
        shopItem.setDisplayName("Desk Cable Clip");
        shopItem.setOriginalFilename("desk-cable-clip.stl");
        shopItem.setShopProductName("Desk Cable Clip");
        shopItem.setShopVariantLabel("Coral Red");
        shopItem.setQuantity(2);
        shopItem.setUnitPriceChf(new BigDecimal("14.90"));
        shopItem.setLineTotalChf(new BigDecimal("29.80"));

        OrderItem printItem = new OrderItem();
        printItem.setItemType("PRINT_FILE");
        printItem.setDisplayName("gear-cover.stl");
        printItem.setOriginalFilename("gear-cover.stl");
        printItem.setMaterialCode("PLA");
        printItem.setColorCode("Nero");
        printItem.setNozzleDiameterMm(new BigDecimal("0.40"));
        printItem.setLayerHeightMm(new BigDecimal("0.200"));
        printItem.setInfillPercent(20);
        printItem.setInfillPattern("gyroid");
        printItem.setSupportsEnabled(true);
        printItem.setQuantity(1);
        printItem.setUnitPriceChf(new BigDecimal("7.00"));
        printItem.setLineTotalChf(new BigDecimal("7.00"));

        byte[] pdf = service.generateDocumentPdf(order, List.of(shopItem, printItem), true, qrBillService, null);

        assertNotNull(pdf);
        assertEquals("09.04.2026", service.capturedVariables.get("dueDate"));
        assertTrue(service.capturedVariables.get("paymentTermsText").toString().contains("30 giorni"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> invoiceLineItems = (List<Map<String, Object>>) service.capturedVariables.get("invoiceLineItems");
        assertEquals("Desk Cable Clip - Coral Red", invoiceLineItems.getFirst().get("description"));
        assertEquals("Stampa 3D: gear-cover.stl", invoiceLineItems.get(1).get("description"));
        String settings = invoiceLineItems.get(1).get("printSettings").toString();
        assertTrue(settings.contains("Materiale: PLA"));
        assertTrue(settings.contains("Colore: Nero"));
        assertTrue(settings.contains("Ugello: 0.40 mm"));
        assertTrue(settings.contains("Altezza strato: 0.200 mm"));
        assertTrue(settings.contains("Riempimento: 20%"));
        assertTrue(settings.contains("Schema riempimento: Giroide"));
        assertTrue(settings.contains("Supporti: Sì"));

        var resolver = new org.thymeleaf.templateresolver.ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/"); resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML"); resolver.setCharacterEncoding("UTF-8");
        var engine = new org.thymeleaf.spring6.SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        byte[] rendered = new InvoicePdfRenderingService(engine)
                .generateInvoicePdfBytesFromTemplate(service.capturedVariables, null);
        try (var document = org.apache.pdfbox.Loader.loadPDF(rendered)) {
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            assertTrue(text.contains("Materiale: PLA"));
            assertTrue(text.contains("30 giorni"));
        }
        java.nio.file.Path preview = java.nio.file.Path.of("build", "invoice-preview.pdf");
        java.nio.file.Files.createDirectories(preview.getParent());
        java.nio.file.Files.write(preview, rendered);
        order.setId(UUID.fromString("12345678-1234-1234-1234-123456789abc"));
        order.setCadHours(new BigDecimal("2.5"));
        order.setCadHourlyRateChf(new BigDecimal("60"));
        order.setCadTotalChf(new BigDecimal("150"));
        order.setSubtotalChf(new BigDecimal("186.80"));
        order.setTotalChf(new BigDecimal("188.80"));
        var realQr = new QrBillService();
        String[] languages = {"it", "en", "de", "fr"};
        String[] receiptLabels = {"Ricevuta", "Receipt", "Empfangsschein", "Récépissé"};
        for (int i = 0; i < languages.length; i++) {
            order.setPreferredLanguage(languages[i]);
            assertEquals(languages[i].toUpperCase(java.util.Locale.ROOT),
                    realQr.createBillFromOrder(order).getFormat().getLanguage().name());
            byte[] localized = new InvoicePdfRenderingService(engine)
                    .generateDocumentPdf(order, List.of(shopItem, printItem), true, realQr, null);
            try (var document = org.apache.pdfbox.Loader.loadPDF(localized)) {
                assertEquals(2, document.getNumberOfPages());
                String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
                var labels = InvoiceLanguage.resolve(languages[i]).labels();
                assertTrue(text.contains(labels.get("invoice")), text);
                assertTrue(text.replaceAll("\\s+", " ").contains(labels.get("supports") + ": " + labels.get("yes")), text);
                assertTrue(new String(realQr.generateQrBillSvg(order), StandardCharsets.UTF_8).contains(receiptLabels[i]));
                assertTrue(text.contains("2.5 h"), text);
                assertTrue(text.contains("CHF 188.80"), text);
            }
            java.nio.file.Files.write(java.nio.file.Path.of("build", "invoice-" + languages[i] + ".pdf"), localized);
        }
        order.setPreferredLanguage("de-CH");
        byte[] multiPage = new InvoicePdfRenderingService(engine).generateDocumentPdf(order,
                java.util.Collections.nCopies(22, printItem), true, realQr, null);
        try (var document = org.apache.pdfbox.Loader.loadPDF(multiPage)) {
            assertTrue(document.getNumberOfPages() > 2);
            var extractor = new org.apache.pdfbox.text.PDFTextStripper();
            extractor.setStartPage(document.getNumberOfPages());
            assertTrue(extractor.getText(document).contains("Zahlung der Rechnung"));
        }
        java.nio.file.Files.write(java.nio.file.Path.of("build", "invoice-multipage.pdf"), multiPage);
        byte[] paid = new InvoicePdfRenderingService(engine).generateDocumentPdf(order,
                List.of(printItem), false, realQr, null);
        try (var document = org.apache.pdfbox.Loader.loadPDF(paid)) {
            assertEquals(1, document.getNumberOfPages());
            assertTrue(new org.apache.pdfbox.text.PDFTextStripper().getText(document).contains("BEZAHLT"));
        }
        java.nio.file.Files.write(java.nio.file.Path.of("build", "invoice-paid.pdf"), paid);
    }

    private static class CapturingInvoicePdfRenderingService extends InvoicePdfRenderingService {
        private Map<String, Object> capturedVariables;

        private CapturingInvoicePdfRenderingService() {
            super(mock(TemplateEngine.class));
        }

        @Override
        public byte[] generateInvoicePdfBytesFromTemplate(Map<String, Object> invoiceTemplateVariables, String qrBillSvg) {
            this.capturedVariables = invoiceTemplateVariables;
            return new byte[]{1, 2, 3};
        }
    }
}
