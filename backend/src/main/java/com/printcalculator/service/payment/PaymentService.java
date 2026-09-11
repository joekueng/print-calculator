package com.printcalculator.service.payment;

import com.printcalculator.entity.Order;
import com.printcalculator.entity.Payment;
import com.printcalculator.event.PaymentReportedEvent;
import com.printcalculator.event.PaymentConfirmedEvent;
import com.printcalculator.repository.OrderRepository;
import com.printcalculator.repository.PaymentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private static final String RECEIVED_STATUS = "RECEIVED";
    private static final String LEGACY_COMPLETED_STATUS = "COMPLETED";

    private final PaymentRepository paymentRepo;
    private final OrderRepository orderRepo;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentService(PaymentRepository paymentRepo,
                          OrderRepository orderRepo,
                          ApplicationEventPublisher eventPublisher) {
        this.paymentRepo = paymentRepo;
        this.orderRepo = orderRepo;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Payment getOrCreatePaymentForOrder(Order order, String defaultMethod) {
        Optional<Payment> existing = paymentRepo.findByOrder_Id(order.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        Payment payment = new Payment();
        payment.setOrder(order);
        // Default to "OTHER" always, as payment method should only be set by the admin explicitly
        payment.setMethod("OTHER");
        payment.setStatus("PENDING");
        payment.setCurrency(order.getCurrency() != null ? order.getCurrency() : "CHF");
        payment.setAmountChf(order.getTotalChf() != null ? order.getTotalChf() : BigDecimal.ZERO);
        payment.setInitiatedAt(OffsetDateTime.now());

        return paymentRepo.save(payment);
    }

    @Transactional
    public Payment reportPayment(UUID orderId, String method) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with id " + orderId));

        Payment payment = paymentRepo.findByOrder_Id(orderId)
                .orElseGet(() -> getOrCreatePaymentForOrder(order, "OTHER"));

        if (!"PENDING".equals(payment.getStatus())) {
            throw new IllegalStateException("Payment is not in PENDING state. Current state: " + payment.getStatus());
        }

        payment.setStatus("REPORTED");
        payment.setReportedAt(OffsetDateTime.now());
        
        // We intentionally do not update the payment method here based on user input,
        // because the system cannot reliably determine the actual method without an integration.
        // It will be updated by the backoffice admin manually.

        payment = paymentRepo.save(payment);

        eventPublisher.publishEvent(new PaymentReportedEvent(this, order, payment));

        return payment;
    }

    @Transactional
    public Payment confirmPayment(UUID orderId, String method) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with id " + orderId));

        Payment payment = paymentRepo.findByOrder_Id(orderId)
                .orElseGet(() -> getOrCreatePaymentForOrder(order, method != null ? method : "OTHER"));

        if (RECEIVED_STATUS.equals(payment.getStatus()) || LEGACY_COMPLETED_STATUS.equals(payment.getStatus())) {
            order.setStatus("PAID");
            if (order.getPaidAt() == null) {
                order.setPaidAt(OffsetDateTime.now());
            }
            orderRepo.save(order);
            return payment;
        }

        payment.setStatus(RECEIVED_STATUS);
        if (method != null && !method.isBlank()) {
            payment.setMethod(method.toUpperCase());
        }
        payment.setReceivedAt(OffsetDateTime.now());
        payment = paymentRepo.save(payment);

        order.setStatus("PAID");
        order.setPaidAt(OffsetDateTime.now());
        orderRepo.save(order);

        eventPublisher.publishEvent(new PaymentConfirmedEvent(this, order, payment));

        return payment;
    }

    @Transactional
    public Payment updatePaymentMethod(UUID orderId, String method) {
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("Payment method is required");
        }

        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with id " + orderId));

        Payment payment = paymentRepo.findByOrder_Id(orderId)
                .orElseGet(() -> getOrCreatePaymentForOrder(order, "OTHER"));

        payment.setMethod(method.trim().toUpperCase());
        return paymentRepo.save(payment);
    }
}
