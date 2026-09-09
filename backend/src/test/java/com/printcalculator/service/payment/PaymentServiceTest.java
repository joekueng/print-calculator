package com.printcalculator.service.payment;

import com.printcalculator.entity.Order;
import com.printcalculator.entity.Payment;
import com.printcalculator.event.PaymentConfirmedEvent;
import com.printcalculator.repository.OrderRepository;
import com.printcalculator.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void confirmPayment_marksOrderPaidAndPublishesPaymentConfirmedEvent() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setStatus("PENDING_PAYMENT");
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setStatus("PENDING");

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_Id(orderId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        new PaymentService(paymentRepository, orderRepository, eventPublisher)
                .confirmPayment(orderId, "bank_transfer");

        assertEquals("PAID", order.getStatus());
        assertNotNull(order.getPaidAt());
        assertEquals("RECEIVED", payment.getStatus());
        assertEquals("BANK_TRANSFER", payment.getMethod());
        ArgumentCaptor<PaymentConfirmedEvent> eventCaptor = ArgumentCaptor.forClass(PaymentConfirmedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(order, eventCaptor.getValue().getOrder());
        assertEquals(payment, eventCaptor.getValue().getPayment());
    }

    @Test
    void confirmPayment_whenAlreadyReceived_doesNotPublishAnotherEmailEvent() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setStatus("IN_PRODUCTION");
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setStatus("RECEIVED");

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_Id(orderId)).thenReturn(Optional.of(payment));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        new PaymentService(paymentRepository, orderRepository, eventPublisher)
                .confirmPayment(orderId, "TWINT");

        assertEquals("PAID", order.getStatus());
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void confirmPayment_whenLegacyCompleted_doesNotPublishAnotherEmailEvent() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setStatus("IN_PRODUCTION");
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setStatus("COMPLETED");

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_Id(orderId)).thenReturn(Optional.of(payment));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        new PaymentService(paymentRepository, orderRepository, eventPublisher)
                .confirmPayment(orderId, "TWINT");

        assertEquals("PAID", order.getStatus());
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(eventPublisher, never()).publishEvent(any());
    }
}
