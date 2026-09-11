package com.printcalculator.service.order;

import com.printcalculator.dto.AddressDto;
import com.printcalculator.dto.CreateOrderRequest;
import com.printcalculator.dto.OrderDto;
import com.printcalculator.dto.OrderItemDto;
import com.printcalculator.entity.Order;
import com.printcalculator.entity.OrderItem;
import com.printcalculator.entity.Payment;
import com.printcalculator.repository.OrderItemRepository;
import com.printcalculator.repository.OrderRepository;
import com.printcalculator.repository.PaymentRepository;
import com.printcalculator.service.OrderService;
import com.printcalculator.service.payment.InvoicePdfRenderingService;
import com.printcalculator.service.payment.PaymentService;
import com.printcalculator.service.payment.QrBillService;
import com.printcalculator.service.payment.TwintPaymentService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class OrderControllerService {
    private static final Set<String> PERSONAL_DATA_REDACTED_STATUSES = Set.of(
            "IN_PRODUCTION",
            "SHIPPED",
            "COMPLETED"
    );

    private final OrderService orderService;
    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final InvoicePdfRenderingService invoiceService;
    private final QrBillService qrBillService;
    private final TwintPaymentService twintPaymentService;
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepo;
    private final OrderCadFileService orderCadFileService;

    public OrderControllerService(OrderService orderService,
                                  OrderRepository orderRepo,
                                  OrderItemRepository orderItemRepo,
                                  InvoicePdfRenderingService invoiceService,
                                  QrBillService qrBillService,
                                  TwintPaymentService twintPaymentService,
                                  PaymentService paymentService,
                                  PaymentRepository paymentRepo,
                                  OrderCadFileService orderCadFileService) {
        this.orderService = orderService;
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.invoiceService = invoiceService;
        this.qrBillService = qrBillService;
        this.twintPaymentService = twintPaymentService;
        this.paymentService = paymentService;
        this.paymentRepo = paymentRepo;
        this.orderCadFileService = orderCadFileService;
    }

    @Transactional
    public OrderDto createOrderFromQuote(UUID quoteSessionId, CreateOrderRequest request) {
        Order order = orderService.createOrderFromQuote(quoteSessionId, request);
        List<OrderItem> items = orderItemRepo.findByOrder_Id(order.getId());
        return convertToDto(order, items);
    }

    public Optional<OrderDto> getOrder(UUID orderId) {
        return orderRepo.findById(orderId)
                .map(order -> {
                    List<OrderItem> items = orderItemRepo.findByOrder_Id(order.getId());
                    return convertToDto(order, items);
                });
    }

    @Transactional
    public Optional<OrderDto> reportPayment(UUID orderId, String method) {
        paymentService.reportPayment(orderId, method);
        return getOrder(orderId);
    }

    public ResponseEntity<byte[]> getConfirmation(UUID orderId) {
        return generateDocument(orderId, true);
    }

    public ResponseEntity<?> downloadCadFiles(UUID orderId) {
        return orderCadFileService.downloadCustomerCadFiles(orderId);
    }

    public ResponseEntity<Map<String, String>> getTwintPayment(UUID orderId) {
        Order order = orderRepo.findById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }

        byte[] qrPng = twintPaymentService.generateQrPng(order, 360);
        String qrDataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(qrPng);

        Map<String, String> data = new HashMap<>();
        data.put("paymentUrl", twintPaymentService.getTwintPaymentUrl(order));
        data.put("openUrl", "/api/orders/" + orderId + "/twint/open");
        data.put("qrImageUrl", "/api/orders/" + orderId + "/twint/qr");
        data.put("qrImageDataUri", qrDataUri);
        return ResponseEntity.ok(data);
    }

    public ResponseEntity<Void> openTwintPayment(UUID orderId) {
        Order order = orderRepo.findById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.status(302)
                .location(URI.create(twintPaymentService.getTwintPaymentUrl(order)))
                .build();
    }

    public ResponseEntity<byte[]> getTwintQr(UUID orderId, int size) {
        Order order = orderRepo.findById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }

        int normalizedSize = Math.max(200, Math.min(size, 600));
        byte[] png = twintPaymentService.generateQrPng(order, normalizedSize);

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }

    private ResponseEntity<byte[]> generateDocument(UUID orderId, boolean isConfirmation) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        List<OrderItem> items = orderItemRepo.findByOrder_Id(orderId);
        Payment payment = paymentRepo.findByOrder_Id(orderId).orElse(null);

        byte[] pdf = invoiceService.generateDocumentPdf(order, items, isConfirmation, qrBillService, payment);
        String typePrefix = isConfirmation ? "confirmation-" : "invoice-";
        String truncatedUuid = order.getId().toString().substring(0, 8);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + typePrefix + truncatedUuid + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(pdf);
    }

    private OrderDto convertToDto(Order order, List<OrderItem> items) {
        OrderDto dto = new OrderDto();
        dto.setId(order.getId());
        dto.setOrderNumber(getDisplayOrderNumber(order));
        dto.setSourceType(order.getSourceType() != null ? order.getSourceType() : "CALCULATOR");
        dto.setStatus(order.getStatus());

        paymentRepo.findByOrder_Id(order.getId()).ifPresent(payment -> {
            dto.setPaymentStatus(payment.getStatus());
            dto.setPaymentMethod(payment.getMethod());
        });

        boolean redactPersonalData = shouldRedactPersonalData(order.getStatus());
        if (!redactPersonalData) {
            dto.setCustomerEmail(order.getCustomerEmail());
            dto.setCustomerPhone(order.getCustomerPhone());
            dto.setBillingCustomerType(order.getBillingCustomerType());
        }
        dto.setPreferredLanguage(order.getPreferredLanguage());
        dto.setCurrency(order.getCurrency());
        dto.setSetupCostChf(order.getSetupCostChf());
        dto.setShippingCostChf(order.getShippingCostChf());
        dto.setDiscountChf(order.getDiscountChf());
        dto.setSubtotalChf(order.getSubtotalChf());
        dto.setIsCadOrder(order.getIsCadOrder());
        dto.setSourceRequestId(order.getSourceRequestId());
        dto.setCadHours(order.getCadHours());
        dto.setCadHourlyRateChf(order.getCadHourlyRateChf());
        dto.setCadTotalChf(order.getCadTotalChf());
        OrderCadFileService.CadFileSummary cadFileSummary = orderCadFileService.summarize(order);
        dto.setCadFileCount(cadFileSummary != null ? cadFileSummary.fileCount() : 0);
        dto.setCadFileDownloadAvailable(cadFileSummary != null && cadFileSummary.downloadAvailable());
        dto.setTotalChf(order.getTotalChf());
        dto.setCreatedAt(order.getCreatedAt());
        dto.setPaidAt(order.getPaidAt());
        dto.setShippingSameAsBilling(order.getShippingSameAsBilling());

        if (!redactPersonalData) {
            AddressDto billing = new AddressDto();
            billing.setFirstName(order.getBillingFirstName());
            billing.setLastName(order.getBillingLastName());
            billing.setCompanyName(order.getBillingCompanyName());
            billing.setContactPerson(order.getBillingContactPerson());
            billing.setAddressLine1(order.getBillingAddressLine1());
            billing.setAddressLine2(order.getBillingAddressLine2());
            billing.setZip(order.getBillingZip());
            billing.setCity(order.getBillingCity());
            billing.setCountryCode(order.getBillingCountryCode());
            dto.setBillingAddress(billing);

            if (!Boolean.TRUE.equals(order.getShippingSameAsBilling())) {
                AddressDto shipping = new AddressDto();
                shipping.setFirstName(order.getShippingFirstName());
                shipping.setLastName(order.getShippingLastName());
                shipping.setCompanyName(order.getShippingCompanyName());
                shipping.setContactPerson(order.getShippingContactPerson());
                shipping.setAddressLine1(order.getShippingAddressLine1());
                shipping.setAddressLine2(order.getShippingAddressLine2());
                shipping.setZip(order.getShippingZip());
                shipping.setCity(order.getShippingCity());
                shipping.setCountryCode(order.getShippingCountryCode());
                dto.setShippingAddress(shipping);
            }
        }

        List<OrderItemDto> itemDtos = items.stream().map(item -> {
            OrderItemDto itemDto = new OrderItemDto();
            itemDto.setId(item.getId());
            itemDto.setItemType(item.getItemType() != null ? item.getItemType() : "PRINT_FILE");
            itemDto.setOriginalFilename(item.getOriginalFilename());
            itemDto.setDisplayName(
                    item.getDisplayName() != null && !item.getDisplayName().isBlank()
                            ? item.getDisplayName()
                            : item.getOriginalFilename()
            );
            itemDto.setMaterialCode(item.getMaterialCode());
            itemDto.setColorCode(item.getColorCode());
            if (item.getShopProduct() != null) {
                itemDto.setShopProductId(item.getShopProduct().getId());
            }
            if (item.getShopProductVariant() != null) {
                itemDto.setShopProductVariantId(item.getShopProductVariant().getId());
            }
            itemDto.setShopProductSlug(item.getShopProductSlug());
            itemDto.setShopProductName(item.getShopProductName());
            itemDto.setShopVariantLabel(item.getShopVariantLabel());
            itemDto.setShopVariantColorName(item.getShopVariantColorName());
            itemDto.setShopVariantColorLabelIt(item.getShopProductVariant() != null ? item.getShopProductVariant().getColorLabelIt() : null);
            itemDto.setShopVariantColorLabelEn(item.getShopProductVariant() != null ? item.getShopProductVariant().getColorLabelEn() : null);
            itemDto.setShopVariantColorLabelDe(item.getShopProductVariant() != null ? item.getShopProductVariant().getColorLabelDe() : null);
            itemDto.setShopVariantColorLabelFr(item.getShopProductVariant() != null ? item.getShopProductVariant().getColorLabelFr() : null);
            itemDto.setShopVariantColorHex(item.getShopVariantColorHex());
            if (item.getFilamentVariant() != null) {
                itemDto.setFilamentVariantId(item.getFilamentVariant().getId());
                itemDto.setFilamentVariantDisplayName(item.getFilamentVariant().getVariantDisplayName());
                itemDto.setFilamentColorName(item.getFilamentVariant().getColorName());
                itemDto.setFilamentColorLabelIt(item.getFilamentVariant().getColorLabelIt());
                itemDto.setFilamentColorLabelEn(item.getFilamentVariant().getColorLabelEn());
                itemDto.setFilamentColorLabelDe(item.getFilamentVariant().getColorLabelDe());
                itemDto.setFilamentColorLabelFr(item.getFilamentVariant().getColorLabelFr());
                itemDto.setFilamentColorHex(item.getFilamentVariant().getColorHex());
            }
            itemDto.setQuality(item.getQuality());
            itemDto.setNozzleDiameterMm(item.getNozzleDiameterMm());
            itemDto.setLayerHeightMm(item.getLayerHeightMm());
            itemDto.setInfillPercent(item.getInfillPercent());
            itemDto.setInfillPattern(item.getInfillPattern());
            itemDto.setSupportsEnabled(item.getSupportsEnabled());
            itemDto.setRequiresSplitPrinting(Boolean.TRUE.equals(item.getRequiresSplitPrinting()));
            itemDto.setQuantity(item.getQuantity());
            itemDto.setPrintTimeSeconds(item.getPrintTimeSeconds());
            itemDto.setMaterialGrams(item.getMaterialGrams());
            itemDto.setUnitPriceChf(item.getUnitPriceChf());
            itemDto.setLineTotalChf(item.getLineTotalChf());
            return itemDto;
        }).collect(Collectors.toList());
        dto.setItems(itemDtos);

        return dto;
    }

    private boolean shouldRedactPersonalData(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        return PERSONAL_DATA_REDACTED_STATUSES.contains(status.trim().toUpperCase(Locale.ROOT));
    }

    private String getDisplayOrderNumber(Order order) {
        String orderNumber = order.getOrderNumber();
        if (orderNumber != null && !orderNumber.isBlank()) {
            return orderNumber;
        }
        return order.getId() != null ? order.getId().toString() : "unknown";
    }
}
