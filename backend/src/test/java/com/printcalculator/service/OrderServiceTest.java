package com.printcalculator.service;

import com.printcalculator.dto.AddressDto;
import com.printcalculator.dto.CreateOrderRequest;
import com.printcalculator.dto.CustomerDto;
import com.printcalculator.entity.Customer;
import com.printcalculator.entity.Order;
import com.printcalculator.entity.OrderItem;
import com.printcalculator.entity.Payment;
import com.printcalculator.entity.QuoteLineItem;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.entity.ShopCategory;
import com.printcalculator.entity.ShopProduct;
import com.printcalculator.entity.ShopProductVariant;
import com.printcalculator.event.OrderCreatedEvent;
import com.printcalculator.repository.CustomerRepository;
import com.printcalculator.repository.OrderItemRepository;
import com.printcalculator.repository.OrderRepository;
import com.printcalculator.repository.QuoteLineItemRepository;
import com.printcalculator.repository.QuoteSessionRepository;
import com.printcalculator.service.payment.InvoicePdfRenderingService;
import com.printcalculator.service.payment.PaymentService;
import com.printcalculator.service.payment.QrBillService;
import com.printcalculator.service.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepo;
    @Mock
    private OrderItemRepository orderItemRepo;
    @Mock
    private QuoteSessionRepository quoteSessionRepo;
    @Mock
    private QuoteLineItemRepository quoteLineItemRepo;
    @Mock
    private CustomerRepository customerRepo;
    @Mock
    private StorageService storageService;
    @Mock
    private InvoicePdfRenderingService invoiceService;
    @Mock
    private QrBillService qrBillService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private PaymentService paymentService;
    @Mock
    private QuoteSessionTotalsService quoteSessionTotalsService;
    @Mock
    private MaterialPrintCompatibilityService materialPrintCompatibilityService;

    @InjectMocks
    private OrderService service;

    @Test
    void createOrderFromQuote_validatesCalculatorMaterialSettingsBeforeCreatingCustomer() {
        UUID sessionId = UUID.randomUUID();
        QuoteSession session = new QuoteSession();
        session.setId(sessionId);
        session.setStatus("ACTIVE");

        QuoteLineItem qItem = new QuoteLineItem();
        qItem.setLineItemType("PRINT_FILE");
        qItem.setNozzleDiameterMm(new BigDecimal("0.20"));
        qItem.setLayerHeightMm(new BigDecimal("0.120"));

        when(quoteSessionRepo.findById(sessionId)).thenReturn(Optional.of(session));
        when(quoteLineItemRepo.findByQuoteSessionId(sessionId)).thenReturn(List.of(qItem));
        doThrow(new IllegalArgumentException("invalid technical material settings"))
                .when(materialPrintCompatibilityService)
                .validate(qItem.getFilamentVariant(), qItem.getNozzleDiameterMm(), qItem.getLayerHeightMm());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.createOrderFromQuote(sessionId, buildRequest())
        );

        assertEquals("invalid technical material settings", exception.getMessage());
        verify(customerRepo, never()).findByEmail(any());
        verify(orderRepo, never()).save(any());
    }

    @Test
    void createOrderFromQuote_withShopCart_shouldPreserveShopSnapshotAndMaterialCode() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();

        QuoteSession session = new QuoteSession();
        session.setId(sessionId);
        session.setStatus("ACTIVE");
        session.setSessionType("SHOP_CART");
        session.setMaterialCode("SHOP");
        session.setPricingVersion("v1");
        session.setSetupCostChf(BigDecimal.ZERO);
        session.setExpiresAt(OffsetDateTime.now().plusDays(30));

        ShopCategory category = new ShopCategory();
        category.setId(UUID.randomUUID());
        category.setSlug("cable-management");
        category.setName("Cable Management");

        ShopProduct product = new ShopProduct();
        product.setId(UUID.randomUUID());
        product.setCategory(category);
        product.setSlug("desk-cable-clip");
        product.setName("Desk Cable Clip");

        ShopProductVariant variant = new ShopProductVariant();
        variant.setId(UUID.randomUUID());
        variant.setProduct(product);
        variant.setVariantLabel("Coral Red");
        variant.setColorName("Coral Red");
        variant.setColorHex("#ff6b6b");
        variant.setInternalMaterialCode("PLA-MATTE");
        variant.setPriceChf(new BigDecimal("14.90"));

        Path sourceDir = Path.of("storage_quotes").toAbsolutePath().normalize().resolve(sessionId.toString());
        Files.createDirectories(sourceDir);
        Path sourceFile = sourceDir.resolve("shop-product.stl");
        Files.writeString(sourceFile, "solid product\nendsolid product\n", StandardCharsets.UTF_8);

        QuoteLineItem qItem = new QuoteLineItem();
        qItem.setId(UUID.randomUUID());
        qItem.setQuoteSession(session);
        qItem.setStatus("READY");
        qItem.setLineItemType("SHOP_PRODUCT");
        qItem.setOriginalFilename("shop-product.stl");
        qItem.setDisplayName("Desk Cable Clip");
        qItem.setQuantity(2);
        qItem.setColorCode("Coral Red");
        qItem.setMaterialCode("PLA-MATTE");
        qItem.setShopProduct(product);
        qItem.setShopProductVariant(variant);
        qItem.setShopProductSlug(product.getSlug());
        qItem.setShopProductName(product.getName());
        qItem.setShopVariantLabel("Coral Red");
        qItem.setShopVariantColorName("Coral Red");
        qItem.setShopVariantColorHex("#ff6b6b");
        qItem.setBoundingBoxXMm(new BigDecimal("60.000"));
        qItem.setBoundingBoxYMm(new BigDecimal("40.000"));
        qItem.setBoundingBoxZMm(new BigDecimal("20.000"));
        qItem.setUnitPriceChf(new BigDecimal("14.90"));
        qItem.setStoredPath(sourceFile.toString());

        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setEmail("buyer@example.com");

        when(quoteSessionRepo.findById(sessionId)).thenReturn(Optional.of(session));
        when(customerRepo.findByEmail("buyer@example.com")).thenReturn(Optional.empty());
        when(customerRepo.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(customer.getId());
            }
            return saved;
        });
        when(quoteLineItemRepo.findByQuoteSessionId(sessionId)).thenReturn(List.of(qItem));
        when(quoteSessionTotalsService.compute(eq(session), eq(List.of(qItem)))).thenReturn(
                new QuoteSessionTotalsService.QuoteSessionTotals(
                        new BigDecimal("29.80"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("29.80"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("2.00"),
                        new BigDecimal("31.80"),
                        BigDecimal.ZERO
                )
        );
        when(orderRepo.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(orderId);
            }
            return saved;
        });
        when(orderItemRepo.save(any(OrderItem.class))).thenAnswer(invocation -> {
            OrderItem saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(orderItemId);
            }
            return saved;
        });
        when(qrBillService.generateQrBillSvg(any(Order.class))).thenReturn("<svg/>".getBytes(StandardCharsets.UTF_8));
        when(invoiceService.generateDocumentPdf(any(Order.class), any(List.class), eq(true), eq(qrBillService), isNull()))
                .thenReturn("pdf".getBytes(StandardCharsets.UTF_8));
        when(paymentService.getOrCreatePaymentForOrder(any(Order.class), eq("OTHER"))).thenReturn(new Payment());

        Order order = service.createOrderFromQuote(sessionId, buildRequest());

        assertEquals(orderId, order.getId());
        assertEquals("SHOP", order.getSourceType());
        assertEquals("CONVERTED", session.getStatus());
        assertEquals(orderId, session.getConvertedOrderId());
        assertAmountEquals("29.80", order.getSubtotalChf());
        assertAmountEquals("31.80", order.getTotalChf());

        ArgumentCaptor<OrderItem> itemCaptor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemRepo, times(2)).save(itemCaptor.capture());
        OrderItem savedItem = itemCaptor.getAllValues().getLast();
        assertEquals("SHOP_PRODUCT", savedItem.getItemType());
        assertEquals("Desk Cable Clip", savedItem.getDisplayName());
        assertEquals("PLA-MATTE", savedItem.getMaterialCode());
        assertEquals("desk-cable-clip", savedItem.getShopProductSlug());
        assertEquals("Desk Cable Clip", savedItem.getShopProductName());
        assertEquals("Coral Red", savedItem.getShopVariantLabel());
        assertEquals("Coral Red", savedItem.getShopVariantColorName());
        assertEquals("#ff6b6b", savedItem.getShopVariantColorHex());
        assertAmountEquals("14.90", savedItem.getUnitPriceChf());
        assertAmountEquals("29.80", savedItem.getLineTotalChf());

        verify(storageService).store(eq(sourceFile), eq(Path.of(
                "orders", orderId.toString(), "3d-files", orderItemId.toString(), savedItem.getStoredFilename()
        )));
        verify(paymentService).getOrCreatePaymentForOrder(order, "OTHER");
        verify(eventPublisher).publishEvent(any(OrderCreatedEvent.class));
    }

    @Test
    void createOrderFromQuote_withShopProductMissingSourceFile_shouldNotFail() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();

        QuoteSession session = new QuoteSession();
        session.setId(sessionId);
        session.setStatus("ACTIVE");
        session.setSessionType("SHOP_CART");
        session.setMaterialCode("SHOP");
        session.setPricingVersion("v1");
        session.setSetupCostChf(BigDecimal.ZERO);
        session.setExpiresAt(OffsetDateTime.now().plusDays(30));

        ShopCategory category = new ShopCategory();
        category.setId(UUID.randomUUID());
        category.setSlug("desk");
        category.setName("Desk");

        ShopProduct product = new ShopProduct();
        product.setId(UUID.randomUUID());
        product.setCategory(category);
        product.setSlug("organizer");
        product.setName("Organizer");

        ShopProductVariant variant = new ShopProductVariant();
        variant.setId(UUID.randomUUID());
        variant.setProduct(product);
        variant.setVariantLabel("PLA");
        variant.setColorName("Orange");
        variant.setColorHex("#ff8a00");
        variant.setInternalMaterialCode("PLA");
        variant.setPriceChf(new BigDecimal("18.00"));

        Path missingSource = Path.of("storage_quotes")
                .toAbsolutePath()
                .normalize()
                .resolve(sessionId.toString())
                .resolve("missing-shop-item.stl");

        QuoteLineItem qItem = new QuoteLineItem();
        qItem.setId(UUID.randomUUID());
        qItem.setQuoteSession(session);
        qItem.setStatus("READY");
        qItem.setLineItemType("SHOP_PRODUCT");
        qItem.setOriginalFilename("organizer.stl");
        qItem.setDisplayName("Organizer");
        qItem.setQuantity(1);
        qItem.setColorCode("Orange");
        qItem.setMaterialCode("PLA");
        qItem.setShopProduct(product);
        qItem.setShopProductVariant(variant);
        qItem.setShopProductSlug(product.getSlug());
        qItem.setShopProductName(product.getName());
        qItem.setShopVariantLabel("PLA");
        qItem.setShopVariantColorName("Orange");
        qItem.setShopVariantColorHex("#ff8a00");
        qItem.setUnitPriceChf(new BigDecimal("18.00"));
        qItem.setStoredPath(missingSource.toString());

        when(quoteSessionRepo.findById(sessionId)).thenReturn(Optional.of(session));
        when(customerRepo.findByEmail("buyer@example.com")).thenReturn(Optional.empty());
        when(customerRepo.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
        when(quoteLineItemRepo.findByQuoteSessionId(sessionId)).thenReturn(List.of(qItem));
        when(quoteSessionTotalsService.compute(eq(session), eq(List.of(qItem)))).thenReturn(
                new QuoteSessionTotalsService.QuoteSessionTotals(
                        new BigDecimal("18.00"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("18.00"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("18.00"),
                        BigDecimal.ZERO
                )
        );
        when(orderRepo.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(orderId);
            }
            return saved;
        });
        when(orderItemRepo.save(any(OrderItem.class))).thenAnswer(invocation -> {
            OrderItem saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(orderItemId);
            }
            return saved;
        });
        when(qrBillService.generateQrBillSvg(any(Order.class))).thenReturn("<svg/>".getBytes(StandardCharsets.UTF_8));
        when(invoiceService.generateDocumentPdf(any(Order.class), any(List.class), eq(true), eq(qrBillService), isNull()))
                .thenReturn("pdf".getBytes(StandardCharsets.UTF_8));
        when(paymentService.getOrCreatePaymentForOrder(any(Order.class), eq("OTHER"))).thenReturn(new Payment());

        Order order = service.createOrderFromQuote(sessionId, buildRequest());

        assertEquals(orderId, order.getId());
        assertEquals("CONVERTED", session.getStatus());

        ArgumentCaptor<OrderItem> itemCaptor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemRepo, times(2)).save(itemCaptor.capture());
        OrderItem savedItem = itemCaptor.getAllValues().getLast();
        assertEquals("PENDING", savedItem.getStoredRelativePath());
        assertNull(savedItem.getFileSizeBytes());

        verify(storageService, never()).store(eq(missingSource), any(Path.class));
        verify(paymentService).getOrCreatePaymentForOrder(order, "OTHER");
    }

    @Test
    void createOrderFromQuote_withCalculatorItemMissingSourceFile_shouldFail() {
        UUID sessionId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();

        QuoteSession session = new QuoteSession();
        session.setId(sessionId);
        session.setStatus("ACTIVE");
        session.setSessionType("QUOTE");
        session.setMaterialCode("PLA");
        session.setPricingVersion("v1");
        session.setSetupCostChf(BigDecimal.ZERO);
        session.setExpiresAt(OffsetDateTime.now().plusDays(30));

        Path missingSource = Path.of("storage_quotes")
                .toAbsolutePath()
                .normalize()
                .resolve(sessionId.toString())
                .resolve("missing-calculator-item.stl");

        QuoteLineItem qItem = new QuoteLineItem();
        qItem.setId(UUID.randomUUID());
        qItem.setQuoteSession(session);
        qItem.setStatus("READY");
        qItem.setLineItemType("PRINT_FILE");
        qItem.setOriginalFilename("part.stl");
        qItem.setDisplayName("part.stl");
        qItem.setQuantity(1);
        qItem.setMaterialCode("PLA");
        qItem.setUnitPriceChf(new BigDecimal("9.50"));
        qItem.setStoredPath(missingSource.toString());

        when(quoteSessionRepo.findById(sessionId)).thenReturn(Optional.of(session));
        when(customerRepo.findByEmail("buyer@example.com")).thenReturn(Optional.empty());
        when(customerRepo.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
        when(quoteLineItemRepo.findByQuoteSessionId(sessionId)).thenReturn(List.of(qItem));
        when(quoteSessionTotalsService.compute(eq(session), eq(List.of(qItem)))).thenReturn(
                new QuoteSessionTotalsService.QuoteSessionTotals(
                        new BigDecimal("9.50"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("9.50"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("9.50"),
                        BigDecimal.ZERO
                )
        );
        when(orderRepo.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(orderId);
            }
            return saved;
        });
        when(orderItemRepo.save(any(OrderItem.class))).thenAnswer(invocation -> {
            OrderItem saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(orderItemId);
            }
            return saved;
        });

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.createOrderFromQuote(sessionId, buildRequest())
        );

        assertEquals(
                "Source file not available for quote line item " + qItem.getId(),
                exception.getMessage()
        );
        verify(paymentService, never()).getOrCreatePaymentForOrder(any(Order.class), eq("OTHER"));
        verify(eventPublisher, never()).publishEvent(any(OrderCreatedEvent.class));
    }

    private CreateOrderRequest buildRequest() {
        CustomerDto customer = new CustomerDto();
        customer.setEmail("buyer@example.com");
        customer.setPhone("+41790000000");
        customer.setCustomerType("PRIVATE");

        AddressDto billing = new AddressDto();
        billing.setFirstName("Joe");
        billing.setLastName("Buyer");
        billing.setAddressLine1("Via Test 1");
        billing.setZip("6900");
        billing.setCity("Lugano");
        billing.setCountryCode("CH");

        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomer(customer);
        request.setBillingAddress(billing);
        request.setShippingSameAsBilling(true);
        request.setLanguage("it");
        request.setAcceptTerms(true);
        request.setAcceptPrivacy(true);
        return request;
    }

    @Test
    void createOrderRejectsUnquotableShippingBeforePaymentOrEvents() {
        assertShippingRejected("MANUAL_QUOTE", "CH", null);
    }

    @Test
    void createOrderRejectsForeignDestinationAndStaleShippingPrice() {
        assertShippingRejected("QUOTED", "DE", null);
    }

    @Test
    void createOrderRejectsStaleShippingPrice() {
        assertShippingRejected("QUOTED", "CH", BigDecimal.valueOf(4));
    }

    private void assertShippingRejected(String status, String country, BigDecimal expected) {
        UUID id = UUID.randomUUID();
        QuoteSession session = new QuoteSession(); session.setId(id);
        Customer customer = new Customer(); customer.setEmail("buyer@example.com");
        when(quoteSessionRepo.findById(id)).thenReturn(Optional.of(session));
        when(customerRepo.findByEmail("buyer@example.com")).thenReturn(Optional.of(customer));
        when(quoteLineItemRepo.findByQuoteSessionId(id)).thenReturn(List.of());
        when(quoteSessionTotalsService.calculateCadTotal(session)).thenReturn(BigDecimal.ONE);
        var shipping = new ShippingQuoteService.ShippingQuote(status, BigDecimal.valueOf(9),null,null,3,List.of());
        when(quoteSessionTotalsService.compute(session,List.of())).thenReturn(
                new QuoteSessionTotalsService.QuoteSessionTotals(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,
                        BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.valueOf(9),
                        BigDecimal.valueOf(9),BigDecimal.ZERO,shipping));
        CreateOrderRequest request = buildRequest(); request.getBillingAddress().setCountryCode(country);
        request.setExpectedShippingCostChf(expected);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.createOrderFromQuote(id,request));
        verify(orderRepo,never()).save(any(Order.class));
        verify(eventPublisher,never()).publishEvent(any(OrderCreatedEvent.class));
        verify(paymentService,never()).getOrCreatePaymentForOrder(any(Order.class),eq("OTHER"));
    }

    private void assertAmountEquals(String expected, BigDecimal actual) {
        assertTrue(new BigDecimal(expected).compareTo(actual) == 0,
                "Expected " + expected + " but got " + actual);
    }
}
