package com.printcalculator.dto;

import java.math.BigDecimal;

/**
 * Live aggregate values for the admin quote sessions page. A visitor identity
 * is not stored for quote sessions, so user-level reuse is intentionally not
 * inferred from these values.
 */
public class AdminSessionStatisticsDto {
    private long totalSessionCount;
    private long sessionsWithItemsCount;
    private long emptySessionCount;
    private long convertedSessionCount;
    private long paidConvertedSessionCount;
    private long modifiedSessionCount;
    private long expiredAbandonedSessionCount;
    private BigDecimal averageItemsPerActiveSession;
    private BigDecimal paidConversionRatePercent;

    public long getTotalSessionCount() {
        return totalSessionCount;
    }

    public void setTotalSessionCount(long totalSessionCount) {
        this.totalSessionCount = totalSessionCount;
    }

    public long getSessionsWithItemsCount() {
        return sessionsWithItemsCount;
    }

    public void setSessionsWithItemsCount(long sessionsWithItemsCount) {
        this.sessionsWithItemsCount = sessionsWithItemsCount;
    }

    public long getEmptySessionCount() {
        return emptySessionCount;
    }

    public void setEmptySessionCount(long emptySessionCount) {
        this.emptySessionCount = emptySessionCount;
    }

    public long getConvertedSessionCount() {
        return convertedSessionCount;
    }

    public void setConvertedSessionCount(long convertedSessionCount) {
        this.convertedSessionCount = convertedSessionCount;
    }

    public long getPaidConvertedSessionCount() {
        return paidConvertedSessionCount;
    }

    public void setPaidConvertedSessionCount(long paidConvertedSessionCount) {
        this.paidConvertedSessionCount = paidConvertedSessionCount;
    }

    public long getModifiedSessionCount() {
        return modifiedSessionCount;
    }

    public void setModifiedSessionCount(long modifiedSessionCount) {
        this.modifiedSessionCount = modifiedSessionCount;
    }

    public long getExpiredAbandonedSessionCount() {
        return expiredAbandonedSessionCount;
    }

    public void setExpiredAbandonedSessionCount(long expiredAbandonedSessionCount) {
        this.expiredAbandonedSessionCount = expiredAbandonedSessionCount;
    }

    public BigDecimal getAverageItemsPerActiveSession() {
        return averageItemsPerActiveSession;
    }

    public void setAverageItemsPerActiveSession(BigDecimal averageItemsPerActiveSession) {
        this.averageItemsPerActiveSession = averageItemsPerActiveSession;
    }

    public BigDecimal getPaidConversionRatePercent() {
        return paidConversionRatePercent;
    }

    public void setPaidConversionRatePercent(BigDecimal paidConversionRatePercent) {
        this.paidConversionRatePercent = paidConversionRatePercent;
    }
}
