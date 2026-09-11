package com.printcalculator.service;

import com.printcalculator.dto.CreateOrderRequest;
import com.printcalculator.entity.*;
import com.printcalculator.repository.CustomerRepository;
import com.printcalculator.repository.OrderItemRepository;
import com.printcalculator.repository.OrderRepository;
import com.printcalculator.repository.QuoteLineItemRepository;
import com.printcalculator.repository.QuoteSessionRepository;
import com.printcalculator.event.OrderCreatedEvent;
import com.printcalculator.service.payment.InvoicePdfRenderingService;
import com.printcalculator.service.payment.PaymentService;
import com.printcalculator.service.payment.QrBillService;
import com.printcalculator.service.storage.StorageService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class OrderService {
    private static final Path QUOTE_STORAGE_ROOT = Paths.get("storage_quotes").toAbsolutePath().normalize();
    private static final String SHOP_LINE_ITEM_TYPE = "SHOP_PRODUCT";

    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final QuoteSessionRepository quoteSessionRepo;
    private final QuoteLineItemRepository quoteLineItemRepo;
    private final CustomerRepository customerRepo;
    private final StorageService storageService;
    private final InvoicePdfRenderingService invoiceService;
    private final QrBillService qrBillService;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentService paymentService;
    private final QuoteSessionTotalsService quoteSessionTotalsService;
    private final MaterialPrintCompatibilityService materialPrintCompatibilityService;

    public OrderService(OrderRepository orderRepo,
                        OrderItemRepository orderItemRepo,
                        QuoteSessionRepository quoteSessionRepo,
                        QuoteLineItemRepository quoteLineItemRepo,
                        CustomerRepository customerRepo,
                        StorageService storageService,
                        InvoicePdfRenderingService invoiceService,
                        QrBillService qrBillService,
                        ApplicationEventPublisher eventPublisher,
                        PaymentService paymentService,
                        QuoteSessionTotalsService quoteSessionTotalsService,
                        MaterialPrintCompatibilityService materialPrintCompatibilityService) {
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.quoteSessionRepo = quoteSessionRepo;
        this.quoteLineItemRepo = quoteLineItemRepo;
        this.customerRepo = customerRepo;
        this.storageService = storageService;
        this.invoiceService = invoiceService;
        this.qrBillService = qrBillService;
        this.eventPublisher = eventPublisher;
        this.paymentService = paymentService;
        this.quoteSessionTotalsService = quoteSessionTotalsService;
        this.materialPrintCompatibilityService = materialPrintCompatibilityService;
    }

    @Transactional
    public Order createOrderFromQuote(UUID quoteSessionId, CreateOrderRequest request) {
        if (!request.isAcceptTerms() || !request.isAcceptPrivacy()) {
            throw new IllegalArgumentException("Accettazione Termini e Privacy obbligatoria.");
        }

        QuoteSession session = quoteSessionRepo.findById(quoteSessionId)
                .orElseThrow(() -> new RuntimeException("Quote Session not found"));

        if (session.getConvertedOrderId() != null) {
            throw new IllegalStateException("Quote session already converted to order");
        }

        List<QuoteLineItem> quoteItems = quoteLineItemRepo.findByQuoteSessionId(quoteSessionId);
        quoteItems = quoteItems.stream()
                .filter(item -> !"REVIEW_REQUIRED".equalsIgnoreCase(item.getStatus()))
                .toList();
        if (quoteItems.isEmpty() && quoteSessionTotalsService.calculateCadTotal(session).compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Quote session has no orderable items");
        }
        quoteItems.stream()
                .filter(item -> !SHOP_LINE_ITEM_TYPE.equals(item.getLineItemType()))
                .forEach(item -> materialPrintCompatibilityService.validate(
                        item.getFilamentVariant(),
                        item.getNozzleDiameterMm(),
                        item.getLayerHeightMm()
                ));

        Customer customer = customerRepo.findByEmail(request.getCustomer().getEmail())
                .orElseGet(() -> {
                    Customer newC = new Customer();
                    newC.setEmail(request.getCustomer().getEmail());
                    newC.setCustomerType(request.getCustomer().getCustomerType());
                    newC.setCreatedAt(OffsetDateTime.now());
                    newC.setUpdatedAt(OffsetDateTime.now());
                    return customerRepo.save(newC);
                });

        customer.setPhone(request.getCustomer().getPhone());
        customer.setCustomerType(request.getCustomer().getCustomerType());
        
        if (request.getBillingAddress() != null) {
            customer.setFirstName(request.getBillingAddress().getFirstName());
            customer.setLastName(request.getBillingAddress().getLastName());
            customer.setCompanyName(request.getBillingAddress().getCompanyName());
            customer.setContactPerson(request.getBillingAddress().getContactPerson());
        }
        
        customer.setUpdatedAt(OffsetDateTime.now());
        customerRepo.save(customer);

        Order order = new Order();
        order.setSourceQuoteSession(session);
        order.setSourceType(resolveOrderSourceType(session));
        order.setCustomer(customer);
        order.setCustomerEmail(request.getCustomer().getEmail());
        order.setCustomerPhone(request.getCustomer().getPhone());
        order.setStatus("PENDING_PAYMENT");
        order.setCreatedAt(OffsetDateTime.now());
        order.setUpdatedAt(OffsetDateTime.now());
        order.setPreferredLanguage(normalizeLanguage(request.getLanguage()));
        order.setCurrency("CHF");

        order.setBillingCustomerType(request.getCustomer().getCustomerType());
        if (request.getBillingAddress() != null) {
            order.setBillingFirstName(request.getBillingAddress().getFirstName());
            order.setBillingLastName(request.getBillingAddress().getLastName());
            order.setBillingCompanyName(request.getBillingAddress().getCompanyName());
            order.setBillingContactPerson(request.getBillingAddress().getContactPerson());
            order.setBillingAddressLine1(request.getBillingAddress().getAddressLine1());
            order.setBillingAddressLine2(request.getBillingAddress().getAddressLine2());
            order.setBillingZip(request.getBillingAddress().getZip());
            order.setBillingCity(request.getBillingAddress().getCity());
            order.setBillingCountryCode(request.getBillingAddress().getCountryCode() != null ? request.getBillingAddress().getCountryCode() : "CH");
        }

        order.setShippingSameAsBilling(request.isShippingSameAsBilling());
        if (!request.isShippingSameAsBilling() && request.getShippingAddress() != null) {
            order.setShippingFirstName(request.getShippingAddress().getFirstName());
            order.setShippingLastName(request.getShippingAddress().getLastName());
            order.setShippingCompanyName(request.getShippingAddress().getCompanyName());
            order.setShippingContactPerson(request.getShippingAddress().getContactPerson());
            order.setShippingAddressLine1(request.getShippingAddress().getAddressLine1());
            order.setShippingAddressLine2(request.getShippingAddress().getAddressLine2());
            order.setShippingZip(request.getShippingAddress().getZip());
            order.setShippingCity(request.getShippingAddress().getCity());
            order.setShippingCountryCode(request.getShippingAddress().getCountryCode() != null ? request.getShippingAddress().getCountryCode() : "CH");
        } else {
            order.setShippingFirstName(order.getBillingFirstName());
            order.setShippingLastName(order.getBillingLastName());
            order.setShippingCompanyName(order.getBillingCompanyName());
            order.setShippingContactPerson(order.getBillingContactPerson());
            order.setShippingAddressLine1(order.getBillingAddressLine1());
            order.setShippingAddressLine2(order.getBillingAddressLine2());
            order.setShippingZip(order.getBillingZip());
            order.setShippingCity(order.getBillingCity());
            order.setShippingCountryCode(order.getBillingCountryCode());
        }

        QuoteSessionTotalsService.QuoteSessionTotals totals = quoteSessionTotalsService.compute(session, quoteItems);
        if (totals.shippingQuote() != null) {
            if (!totals.shippingQuote().available()) {
                throw new IllegalArgumentException("Shipping requires a completed calculation or a manual quote");
            }
            if (request.getExpectedShippingCostChf() != null
                    && request.getExpectedShippingCostChf().compareTo(totals.shippingCostChf()) != 0) {
                throw new IllegalArgumentException("Shipping price changed. Refresh checkout before placing the order");
            }
            if (!"NOT_REQUIRED".equals(totals.shippingQuote().status())
                    && !"CH".equalsIgnoreCase(order.getShippingCountryCode())) {
                throw new IllegalArgumentException("Automatic calculator shipping is available only within Switzerland");
            }
            order.setShippingQuoteSnapshot(new com.fasterxml.jackson.databind.ObjectMapper().convertValue(
                    totals.shippingQuote(), new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String,Object>>() {}));
            order.getShippingQuoteSnapshot().put("algorithmVersion", "swiss-post-packing-v1-2026-09");
        }
        BigDecimal cadTotal = totals.cadTotalChf();

        BigDecimal subtotal = BigDecimal.ZERO;
        order.setSubtotalChf(BigDecimal.ZERO);
        order.setTotalChf(BigDecimal.ZERO);
        order.setDiscountChf(BigDecimal.ZERO);
        order.setSetupCostChf(totals.setupCostChf());
        order.setShippingCostChf(totals.shippingCostChf());
        order.setIsCadOrder(cadTotal.compareTo(BigDecimal.ZERO) > 0 || "CAD_ACTIVE".equals(session.getStatus()));
        order.setSourceRequestId(session.getSourceRequestId());
        order.setCadHours(session.getCadHours() != null ? session.getCadHours() : BigDecimal.ZERO);
        order.setCadHourlyRateChf(session.getCadHourlyRateChf() != null ? session.getCadHourlyRateChf() : BigDecimal.ZERO);
        order.setCadTotalChf(cadTotal);

        order = orderRepo.save(order);

        List<OrderItem> savedItems = new ArrayList<>();

        for (QuoteLineItem qItem : quoteItems) {
            OrderItem oItem = new OrderItem();
            oItem.setOrder(order);
            oItem.setItemType(qItem.getLineItemType() != null ? qItem.getLineItemType() : "PRINT_FILE");
            oItem.setOriginalFilename(qItem.getOriginalFilename());
            oItem.setDisplayName(
                    qItem.getDisplayName() != null && !qItem.getDisplayName().isBlank()
                            ? qItem.getDisplayName()
                            : qItem.getOriginalFilename()
            );
            int quantity = qItem.getQuantity() != null && qItem.getQuantity() > 0 ? qItem.getQuantity() : 1;
            oItem.setQuantity(quantity);
            oItem.setColorCode(qItem.getColorCode());
            oItem.setFilamentVariant(qItem.getFilamentVariant());
            oItem.setShopProduct(qItem.getShopProduct());
            oItem.setShopProductVariant(qItem.getShopProductVariant());
            oItem.setShopProductSlug(qItem.getShopProductSlug());
            oItem.setShopProductName(qItem.getShopProductName());
            oItem.setShopVariantLabel(qItem.getShopVariantLabel());
            oItem.setShopVariantColorName(qItem.getShopVariantColorName());
            oItem.setShopVariantColorHex(qItem.getShopVariantColorHex());
            if (qItem.getMaterialCode() != null && !qItem.getMaterialCode().isBlank()) {
                oItem.setMaterialCode(qItem.getMaterialCode());
            } else if (qItem.getFilamentVariant() != null
                    && qItem.getFilamentVariant().getFilamentMaterialType() != null
                    && qItem.getFilamentVariant().getFilamentMaterialType().getMaterialCode() != null) {
                oItem.setMaterialCode(qItem.getFilamentVariant().getFilamentMaterialType().getMaterialCode());
            } else {
                oItem.setMaterialCode(session.getMaterialCode());
            }
            oItem.setQuality(qItem.getQuality());
            oItem.setNozzleDiameterMm(qItem.getNozzleDiameterMm());
            oItem.setLayerHeightMm(qItem.getLayerHeightMm());
            oItem.setInfillPercent(qItem.getInfillPercent());
            oItem.setInfillPattern(qItem.getInfillPattern());
            oItem.setSupportsEnabled(qItem.getSupportsEnabled());
            oItem.setRequiresSplitPrinting(Boolean.TRUE.equals(qItem.getRequiresSplitPrinting()));

            BigDecimal distributedUnitPrice = qItem.getUnitPriceChf() != null ? qItem.getUnitPriceChf() : BigDecimal.ZERO;
            if (totals.totalPrintSeconds().compareTo(BigDecimal.ZERO) > 0 && qItem.getPrintTimeSeconds() != null) {
                BigDecimal itemSeconds = BigDecimal.valueOf(qItem.getPrintTimeSeconds()).multiply(BigDecimal.valueOf(quantity));
                BigDecimal share = itemSeconds.divide(totals.totalPrintSeconds(), 8, RoundingMode.HALF_UP);
                BigDecimal itemMachineCost = totals.globalMachineCostChf().multiply(share);
                BigDecimal unitMachineCost = itemMachineCost.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
                distributedUnitPrice = distributedUnitPrice.add(unitMachineCost);
            }

            oItem.setUnitPriceChf(distributedUnitPrice);
            oItem.setLineTotalChf(distributedUnitPrice.multiply(BigDecimal.valueOf(quantity)));
            oItem.setPrintTimeSeconds(qItem.getPrintTimeSeconds());
            oItem.setMaterialGrams(qItem.getMaterialGrams());
            oItem.setBoundingBoxXMm(qItem.getBoundingBoxXMm());
            oItem.setBoundingBoxYMm(qItem.getBoundingBoxYMm());
            oItem.setBoundingBoxZMm(qItem.getBoundingBoxZMm());

            UUID fileUuid = UUID.randomUUID();
            String ext = getExtension(qItem.getOriginalFilename());
            String storedFilename = fileUuid.toString() + "." + ext;

            oItem.setStoredFilename(storedFilename);
            oItem.setStoredRelativePath("PENDING");
            oItem.setMimeType("application/octet-stream");
            oItem.setCreatedAt(OffsetDateTime.now());

            oItem = orderItemRepo.save(oItem);

            Path sourcePath = resolveStoredQuotePath(qItem.getStoredPath(), session.getId());
            if (sourcePath == null || !Files.exists(sourcePath)) {
                if (requiresStoredSourceFile(qItem)) {
                    throw new IllegalStateException("Source file not available for quote line item " + qItem.getId());
                }
            } else {
                String relativePath = "orders/" + order.getId() + "/3d-files/" + oItem.getId() + "/" + storedFilename;
                oItem.setStoredRelativePath(relativePath);
                try {
                    storageService.store(sourcePath, Paths.get(relativePath));
                    oItem.setFileSizeBytes(Files.size(sourcePath));
                } catch (IOException e) {
                    throw new RuntimeException("Failed to copy quote file for line item " + qItem.getId(), e);
                }
            }

            oItem = orderItemRepo.save(oItem);
            savedItems.add(oItem);
            subtotal = subtotal.add(oItem.getLineTotalChf());
        }

        order.setSubtotalChf(subtotal.add(cadTotal));
        
        BigDecimal total = order.getSubtotalChf()
                .add(order.getSetupCostChf())
                .add(order.getShippingCostChf())
                .subtract(order.getDiscountChf() != null ? order.getDiscountChf() : BigDecimal.ZERO);
        order.setTotalChf(total);

        session.setConvertedOrderId(order.getId());
        session.setStatus("CONVERTED");
        quoteSessionRepo.save(session);

        // Generate Invoice and QR Bill
        generateAndSaveDocuments(order, savedItems);

        Order savedOrder = orderRepo.save(order);

        // ALWAYS initialize payment as PENDING
        paymentService.getOrCreatePaymentForOrder(savedOrder, "OTHER");

        eventPublisher.publishEvent(new OrderCreatedEvent(this, savedOrder));

        return savedOrder;
    }
    
    private void generateAndSaveDocuments(Order order, List<OrderItem> items) {
        try {
            // 1. Generate and save the raw QR Bill for internal traceability.
            byte[] qrBillSvgBytes = qrBillService.generateQrBillSvg(order);
            saveFileBytes(qrBillSvgBytes, buildQrBillSvgRelativePath(order));

            // 2. Generate and save the same confirmation PDF served by /api/orders/{id}/confirmation.
            byte[] confirmationPdfBytes = invoiceService.generateDocumentPdf(order, items, true, qrBillService, null);
            saveFileBytes(confirmationPdfBytes, buildConfirmationPdfRelativePath(order));

        } catch (Exception e) {
            e.printStackTrace(); 
            // Don't fail the order if document generation fails, but log it
            // TODO: Better error handling
        }
    }

    private void saveFileBytes(byte[] content, String relativePath) {
        // Since StorageService takes paths, we might need to write to temp first or check if it supports bytes/streams
        // Simulating via temp file for now as StorageService.store takes a Path
        try {
            Path tempFile = Files.createTempFile("print-calc-upload", ".tmp");
            Files.write(tempFile, content);
            storageService.store(tempFile, Paths.get(relativePath));
            Files.delete(tempFile);
        } catch (IOException e) {
             throw new RuntimeException("Failed to save file " + relativePath, e);
        }
    }

    private String getExtension(String filename) {
        if (filename == null) return "stl";
        int i = filename.lastIndexOf('.');
        if (i > 0) {
            return filename.substring(i + 1);
        }
        return "stl";
    }

    private boolean requiresStoredSourceFile(QuoteLineItem qItem) {
        return !SHOP_LINE_ITEM_TYPE.equalsIgnoreCase(
                qItem.getLineItemType() != null ? qItem.getLineItemType() : ""
        );
    }

    private Path resolveStoredQuotePath(String storedPath, UUID expectedSessionId) {
        if (storedPath == null || storedPath.isBlank()) {
            return null;
        }
        try {
            Path raw = Path.of(storedPath).normalize();
            Path resolved = raw.isAbsolute() ? raw : QUOTE_STORAGE_ROOT.resolve(raw).normalize();
            Path expectedSessionRoot = QUOTE_STORAGE_ROOT.resolve(expectedSessionId.toString()).normalize();
            if (!resolved.startsWith(expectedSessionRoot)) {
                return null;
            }
            return resolved;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private String resolveOrderSourceType(QuoteSession session) {
        if (session != null && "SHOP_CART".equalsIgnoreCase(session.getSessionType())) {
            return "SHOP";
        }
        return "CALCULATOR";
    }

    private String getDisplayOrderNumber(Order order) {
        String orderNumber = order.getOrderNumber();
        if (orderNumber != null && !orderNumber.isBlank()) {
            return orderNumber;
        }
        return order.getId() != null ? order.getId().toString() : "unknown";
    }

    private String buildQrBillSvgRelativePath(Order order) {
        return "orders/" + order.getId() + "/documents/qr-bill.svg";
    }

    private String buildConfirmationPdfRelativePath(Order order) {
        return "orders/" + order.getId() + "/documents/confirmation-" + getDisplayOrderNumber(order) + ".pdf";
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "it";
        }

        String normalized = language.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 2) {
            normalized = normalized.substring(0, 2);
        }

        return switch (normalized) {
            case "it", "en", "de", "fr" -> normalized;
            default -> "it";
        };
    }
}
