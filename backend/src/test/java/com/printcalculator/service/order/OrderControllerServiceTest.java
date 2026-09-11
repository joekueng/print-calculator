package com.printcalculator.service.order;

import com.printcalculator.dto.OrderDto;
import com.printcalculator.entity.Order;
import com.printcalculator.entity.OrderItem;
import com.printcalculator.repository.OrderItemRepository;
import com.printcalculator.repository.OrderRepository;
import com.printcalculator.repository.PaymentRepository;
import com.printcalculator.service.OrderService;
import com.printcalculator.service.payment.InvoicePdfRenderingService;
import com.printcalculator.service.payment.PaymentService;
import com.printcalculator.service.payment.QrBillService;
import com.printcalculator.service.payment.TwintPaymentService;
import com.printcalculator.service.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderControllerServiceTest {

    @Mock
    private OrderService orderService;
    @Mock
    private OrderRepository orderRepo;
    @Mock
    private OrderItemRepository orderItemRepo;
    @Mock
    private StorageService storageService;
    @Mock
    private InvoicePdfRenderingService invoiceService;
    @Mock
    private QrBillService qrBillService;
    @Mock
    private TwintPaymentService twintPaymentService;
    @Mock
    private PaymentService paymentService;
    @Mock
    private PaymentRepository paymentRepo;
    @Mock
    private OrderCadFileService orderCadFileService;

    @InjectMocks
    private OrderControllerService service;

    @Test
    void getOrder_withShippedStatus_shouldRedactPersonalData() {
        UUID orderId = UUID.randomUUID();
        Order order = buildOrder(orderId, "SHIPPED");
        OffsetDateTime paidAt = OffsetDateTime.parse("2026-04-21T09:15:00+02:00");
        order.setPaidAt(paidAt);

        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepo.findByOrder_Id(orderId)).thenReturn(List.of());
        when(paymentRepo.findByOrder_Id(orderId)).thenReturn(Optional.empty());

        Optional<OrderDto> result = service.getOrder(orderId);

        assertTrue(result.isPresent());
        OrderDto dto = result.get();
        assertNull(dto.getCustomerEmail());
        assertNull(dto.getCustomerPhone());
        assertNull(dto.getBillingAddress());
        assertNull(dto.getShippingAddress());
        assertEquals(paidAt, dto.getPaidAt());
    }

    @Test
    void getTwintQr_withOversizedInput_shouldClampSizeTo600() {
        UUID orderId = UUID.randomUUID();
        Order order = buildOrder(orderId, "PENDING_PAYMENT");

        byte[] png = new byte[]{1, 2, 3};
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(twintPaymentService.generateQrPng(order, 600)).thenReturn(png);

        ResponseEntity<byte[]> response = service.getTwintQr(orderId, 5000);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(MediaType.IMAGE_PNG, response.getHeaders().getContentType());
        assertArrayEquals(png, response.getBody());
        verify(twintPaymentService).generateQrPng(order, 600);
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
