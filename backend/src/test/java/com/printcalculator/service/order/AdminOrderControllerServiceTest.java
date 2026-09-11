package com.printcalculator.service.order;

import com.printcalculator.dto.AdminOrderStatusUpdateRequest;
import com.printcalculator.dto.AdminOrderStatisticsDto;
import com.printcalculator.dto.OrderDto;
import com.printcalculator.entity.EmailLog;
import com.printcalculator.entity.FilamentVariant;
import com.printcalculator.entity.Order;
import com.printcalculator.entity.OrderItem;
import com.printcalculator.entity.Payment;
import com.printcalculator.event.OrderShippedEvent;
import com.printcalculator.event.listener.OrderEmailListener;
import com.printcalculator.repository.EmailLogRepository;
import com.printcalculator.repository.OrderItemRepository;
import com.printcalculator.repository.OrderRepository;
import com.printcalculator.repository.PaymentRepository;
import com.printcalculator.repository.QuoteLineItemRepository;
import com.printcalculator.service.payment.InvoicePdfRenderingService;
import com.printcalculator.service.payment.PaymentService;
import com.printcalculator.service.payment.QrBillService;
import com.printcalculator.service.email.EmailAuditService;
import com.printcalculator.service.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOrderControllerServiceTest {

    @Mock
    private OrderRepository orderRepo;
    @Mock
    private OrderItemRepository orderItemRepo;
    @Mock
    private PaymentRepository paymentRepo;
    @Mock
    private EmailLogRepository emailLogRepo;
    @Mock
    private QuoteLineItemRepository quoteLineItemRepo;
    @Mock
    private PaymentService paymentService;
    @Mock
    private StorageService storageService;
    @Mock
    private InvoicePdfRenderingService invoiceService;
    @Mock
    private QrBillService qrBillService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private OrderCadFileService orderCadFileService;
    @Mock
    private EmailAuditService emailAuditService;
    @Mock
    private OrderEmailListener orderEmailListener;

    @InjectMocks
    private AdminOrderControllerService service;

    @Test
    void confirmationDownloadRegeneratesPdfInsteadOfServingArchivedLogo() {
        UUID id = UUID.randomUUID();
        Order order = buildOrder(id, "PENDING_PAYMENT");
        when(orderRepo.findById(id)).thenReturn(Optional.of(order));
        when(orderItemRepo.findByOrder_Id(id)).thenReturn(List.of());
        byte[] currentPdf = "current template".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(invoiceService.generateDocumentPdf(order, List.of(), true, qrBillService, null)).thenReturn(currentPdf);

        var response = service.downloadOrderConfirmation(id);

        org.junit.jupiter.api.Assertions.assertArrayEquals(currentPdf, response.getBody());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        org.mockito.Mockito.verifyNoInteractions(storageService);
        verify(invoiceService).generateDocumentPdf(order, List.of(), true, qrBillService, null);
    }

    @Test
    void getStatistics_shouldReturnOnlyRepositoryAggregatesForPaidNonCancelledOrders() {
        when(orderRepo.countPaidNonCancelledForStatistics()).thenReturn(2L);
        when(orderRepo.sumPaidNonCancelledTotalsForStatistics()).thenReturn(new BigDecimal("240.00"));
        when(orderRepo.averagePaidNonCancelledTotalsForStatistics()).thenReturn(120.0);
        when(orderRepo.countUniquePaidNonCancelledCustomersForStatistics()).thenReturn(1L);

        AdminOrderStatisticsDto dto = service.getStatistics();

        assertEquals(2L, dto.getPaidOrderCount());
        assertEquals(new BigDecimal("240.00"), dto.getRevenueChf());
        assertEquals(new BigDecimal("120.00"), dto.getAverageOrderValueChf());
        assertEquals(1L, dto.getUniqueCustomerCount());
    }

    @Test
    void updatePaymentMethod_withBlankMethod_shouldReturnBadRequest() {
        UUID orderId = UUID.randomUUID();
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(buildOrder(orderId, "PENDING_PAYMENT")));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.updatePaymentMethod(orderId, Map.of("method", " "))
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(paymentService, never()).updatePaymentMethod(any(), any());
    }

    @Test
    void updatePaymentMethod_withValidMethod_shouldDelegateAndReturnUpdatedDto() {
        UUID orderId = UUID.randomUUID();
        Order order = buildOrder(orderId, "PENDING_PAYMENT");
        OffsetDateTime paidAt = OffsetDateTime.parse("2026-04-21T09:15:00+02:00");
        order.setPaidAt(paidAt);
        Payment payment = new Payment();
        payment.setMethod("BANK_TRANSFER");
        payment.setStatus("PENDING");

        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepo.findByOrder_Id(orderId)).thenReturn(List.of());
        when(paymentRepo.findByOrder_Id(orderId)).thenReturn(Optional.of(payment));

        OrderDto dto = service.updatePaymentMethod(orderId, Map.of("method", "BANK_TRANSFER"));

        assertEquals("BANK_TRANSFER", dto.getPaymentMethod());
        assertEquals("PENDING", dto.getPaymentStatus());
        assertEquals(paidAt, dto.getPaidAt());
        verify(paymentService).updatePaymentMethod(orderId, "BANK_TRANSFER");
    }

    @Test
    void updateOrderStatus_toShipped_shouldPublishOrderShippedEvent() {
        UUID orderId = UUID.randomUUID();
        Order order = buildOrder(orderId, "PAID");

        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepo.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepo.findByOrder_Id(orderId)).thenReturn(List.of());
        when(paymentRepo.findByOrder_Id(orderId)).thenReturn(Optional.empty());

        AdminOrderStatusUpdateRequest payload = new AdminOrderStatusUpdateRequest();
        payload.setStatus("shipped");

        OrderDto dto = service.updateOrderStatus(orderId, payload);

        assertEquals("SHIPPED", dto.getStatus());
        verify(eventPublisher).publishEvent(any(OrderShippedEvent.class));
    }

    @Test
    void updateOrderStatus_toPaid_shouldConfirmPayment() {
        UUID orderId = UUID.randomUUID();
        Order order = buildOrder(orderId, "PENDING_PAYMENT");
        Payment payment = new Payment();
        payment.setMethod("TWINT");
        payment.setStatus("PENDING");

        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepo.findByOrder_Id(orderId)).thenReturn(List.of());
        when(paymentRepo.findByOrder_Id(orderId)).thenReturn(Optional.of(payment));

        AdminOrderStatusUpdateRequest payload = new AdminOrderStatusUpdateRequest();
        payload.setStatus("PAID");

        service.updateOrderStatus(orderId, payload);

        verify(paymentService).confirmPayment(orderId, "TWINT");
        verify(orderRepo, never()).save(order);
    }

    @Test
    void updateOrderStatus_fromShippedToShipped_shouldNotPublishEvent() {
        UUID orderId = UUID.randomUUID();
        Order order = buildOrder(orderId, "SHIPPED");

        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepo.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepo.findByOrder_Id(orderId)).thenReturn(List.of());
        when(paymentRepo.findByOrder_Id(orderId)).thenReturn(Optional.empty());

        AdminOrderStatusUpdateRequest payload = new AdminOrderStatusUpdateRequest();
        payload.setStatus("SHIPPED");

        service.updateOrderStatus(orderId, payload);

        verify(eventPublisher, never()).publishEvent(any(OrderShippedEvent.class));
    }

    @Test
    void resendEmail_withOrderEmailLog_shouldDelegateAndReturnUpdatedDto() {
        UUID orderId = UUID.randomUUID();
        UUID emailLogId = UUID.randomUUID();
        Order order = buildOrder(orderId, "PAID");
        EmailLog emailLog = new EmailLog();
        emailLog.setId(emailLogId);
        emailLog.setOrder(order);
        emailLog.setEventType("ORDER_CONFIRMATION_CUSTOMER");

        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(emailLogRepo.findById(emailLogId)).thenReturn(Optional.of(emailLog));
        when(orderItemRepo.findByOrder_Id(orderId)).thenReturn(List.of());
        when(paymentRepo.findByOrder_Id(orderId)).thenReturn(Optional.empty());

        OrderDto dto = service.resendEmail(orderId, emailLogId);

        assertEquals(orderId, dto.getId());
        verify(orderEmailListener).resendOrderEmail(order, emailLog);
    }

    @Test
    void downloadOrderItemFile_withInvalidRelativePath_shouldReturnNotFound() {
        UUID orderId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();

        Order order = buildOrder(orderId, "PAID");
        OrderItem item = new OrderItem();
        item.setId(orderItemId);
        item.setOrder(order);
        item.setStoredRelativePath("../escape/path.stl");

        when(orderItemRepo.findById(orderItemId)).thenReturn(Optional.of(item));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.downloadOrderItemFile(orderId, orderItemId)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void getOrder_shouldIncludePerItemPrintSettingsAndVariantMetadata() {
        UUID orderId = UUID.randomUUID();
        Order order = buildOrder(orderId, "PAID");

        FilamentVariant variant = new FilamentVariant();
        variant.setId(42L);
        variant.setVariantDisplayName("PLA Arancione Opaco");
        variant.setColorName("Arancione");
        variant.setColorHex("#ff7a00");

        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setOrder(order);
        item.setOriginalFilename("obj_4_Part 1.stl");
        item.setMaterialCode("PLA");
        item.setColorCode("Arancione");
        item.setFilamentVariant(variant);
        item.setQuality("standard");
        item.setNozzleDiameterMm(new BigDecimal("0.60"));
        item.setLayerHeightMm(new BigDecimal("0.24"));
        item.setInfillPercent(15);
        item.setInfillPattern("grid");
        item.setSupportsEnabled(Boolean.FALSE);
        item.setQuantity(1);
        item.setPrintTimeSeconds(2340);
        item.setMaterialGrams(new BigDecimal("22.76"));
        item.setUnitPriceChf(new BigDecimal("0.99"));
        item.setLineTotalChf(new BigDecimal("0.99"));

        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepo.findByOrder_Id(orderId)).thenReturn(List.of(item));
        when(paymentRepo.findByOrder_Id(orderId)).thenReturn(Optional.empty());

        OrderDto dto = service.getOrder(orderId);

        assertEquals(1, dto.getItems().size());
        var itemDto = dto.getItems().get(0);
        assertEquals(new BigDecimal("0.60"), itemDto.getNozzleDiameterMm());
        assertEquals(new BigDecimal("0.24"), itemDto.getLayerHeightMm());
        assertEquals(15, itemDto.getInfillPercent());
        assertEquals("grid", itemDto.getInfillPattern());
        assertEquals(Boolean.FALSE, itemDto.getSupportsEnabled());
        assertEquals(42L, itemDto.getFilamentVariantId());
        assertEquals("PLA Arancione Opaco", itemDto.getFilamentVariantDisplayName());
        assertEquals("Arancione", itemDto.getFilamentColorName());
        assertEquals("#ff7a00", itemDto.getFilamentColorHex());
    }

    private Order buildOrder(UUID orderId, String status) {
        Order order = new Order();
        order.setId(orderId);
        order.setStatus(status);
        order.setCustomerEmail("customer@example.com");
        order.setCustomerPhone("+41910000000");
        order.setBillingCustomerType("PRIVATE");
        order.setBillingFirstName("Mario");
        order.setBillingLastName("Rossi");
        order.setBillingAddressLine1("Via Test 1");
        order.setBillingZip("6900");
        order.setBillingCity("Lugano");
        order.setBillingCountryCode("CH");
        order.setShippingSameAsBilling(true);
        order.setCurrency("CHF");
        order.setSetupCostChf(BigDecimal.ZERO);
        order.setShippingCostChf(BigDecimal.ZERO);
        order.setDiscountChf(BigDecimal.ZERO);
        order.setSubtotalChf(BigDecimal.ZERO);
        order.setCadTotalChf(BigDecimal.ZERO);
        order.setTotalChf(BigDecimal.ZERO);
        return order;
    }
}
