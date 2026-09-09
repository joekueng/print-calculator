package com.printcalculator.repository;

import com.printcalculator.entity.Order;
import com.printcalculator.entity.Payment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:order-statistics;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryStatisticsTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void paidStatistics_includeReceivedAndLegacyCompletedPayments() {
        saveOrderWithPayment("received@example.test", "PAID", "RECEIVED", "10.00");
        saveOrderWithPayment("RECEIVED@example.test", "IN_PRODUCTION", "COMPLETED", "20.00");
        saveOrderWithPayment("reported@example.test", "PAID", "REPORTED", "30.00");
        saveOrderWithPayment("cancelled@example.test", "CANCELLED", "RECEIVED", "40.00");

        assertEquals(2L, orderRepository.countPaidNonCancelledForStatistics());
        assertEquals(new BigDecimal("30.00"), orderRepository.sumPaidNonCancelledTotalsForStatistics());
        assertEquals(15.0, orderRepository.averagePaidNonCancelledTotalsForStatistics());
        assertEquals(1L, orderRepository.countUniquePaidNonCancelledCustomersForStatistics());
    }

    private void saveOrderWithPayment(String email, String orderStatus, String paymentStatus, String totalChf) {
        BigDecimal total = new BigDecimal(totalChf);
        Order order = new Order();
        order.setSourceType("CALCULATOR");
        order.setStatus(orderStatus);
        order.setCustomerEmail(email);
        order.setBillingCustomerType("PRIVATE");
        order.setBillingAddressLine1("Teststrasse 1");
        order.setBillingZip("8000");
        order.setBillingCity("Zurich");
        order.setBillingCountryCode("CH");
        order.setShippingSameAsBilling(true);
        order.setCurrency("CHF");
        order.setSetupCostChf(BigDecimal.ZERO);
        order.setShippingCostChf(BigDecimal.ZERO);
        order.setDiscountChf(BigDecimal.ZERO);
        order.setSubtotalChf(total);
        order.setIsCadOrder(false);
        order.setCadTotalChf(BigDecimal.ZERO);
        order.setTotalChf(total);
        order = orderRepository.save(order);

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setMethod("OTHER");
        payment.setStatus(paymentStatus);
        payment.setCurrency("CHF");
        payment.setAmountChf(total);
        payment.setInitiatedAt(OffsetDateTime.now());
        paymentRepository.save(payment);
    }
}
