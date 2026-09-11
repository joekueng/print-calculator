package com.printcalculator.dto;

import java.math.BigDecimal;

/**
 * Live aggregate values for the admin orders page. Only completed, non-cancelled
 * payments are included.
 */
public class AdminOrderStatisticsDto {
    private long paidOrderCount;
    private BigDecimal revenueChf;
    private BigDecimal averageOrderValueChf;
    private long uniqueCustomerCount;

    public long getPaidOrderCount() {
        return paidOrderCount;
    }

    public void setPaidOrderCount(long paidOrderCount) {
        this.paidOrderCount = paidOrderCount;
    }

    public BigDecimal getRevenueChf() {
        return revenueChf;
    }

    public void setRevenueChf(BigDecimal revenueChf) {
        this.revenueChf = revenueChf;
    }

    public BigDecimal getAverageOrderValueChf() {
        return averageOrderValueChf;
    }

    public void setAverageOrderValueChf(BigDecimal averageOrderValueChf) {
        this.averageOrderValueChf = averageOrderValueChf;
    }

    public long getUniqueCustomerCount() {
        return uniqueCustomerCount;
    }

    public void setUniqueCustomerCount(long uniqueCustomerCount) {
        this.uniqueCustomerCount = uniqueCustomerCount;
    }
}
