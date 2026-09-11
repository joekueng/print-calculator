package com.printcalculator.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class OrderDto {
    private UUID id;
    private String orderNumber;
    private String sourceType;
    private String status;
    private String paymentStatus;
    private String paymentMethod;
    private String customerEmail;
    private String customerPhone;
    private String preferredLanguage;
    private String billingCustomerType;
    private AddressDto billingAddress;
    private AddressDto shippingAddress;
    private Boolean shippingSameAsBilling;
    private String currency;
    private BigDecimal setupCostChf;
    private BigDecimal shippingCostChf;
    private BigDecimal discountChf;
    private BigDecimal subtotalChf;
    private Boolean isCadOrder;
    private UUID sourceRequestId;
    private BigDecimal cadHours;
    private BigDecimal cadHourlyRateChf;
    private BigDecimal cadTotalChf;
    private Integer cadFileCount;
    private Boolean cadFileDownloadAvailable;
    private List<OrderDeliverableFileDto> cadFiles;
    private BigDecimal totalChf;
    private OffsetDateTime createdAt;
    private OffsetDateTime paidAt;
    private String printMaterialCode;
    private BigDecimal printNozzleDiameterMm;
    private BigDecimal printLayerHeightMm;
    private String printInfillPattern;
    private Integer printInfillPercent;
    private Boolean printSupportsEnabled;
    private List<AdminEmailLogDto> emailLogs;
    private List<OrderItemDto> items;

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }

    public String getBillingCustomerType() { return billingCustomerType; }
    public void setBillingCustomerType(String billingCustomerType) { this.billingCustomerType = billingCustomerType; }

    public AddressDto getBillingAddress() { return billingAddress; }
    public void setBillingAddress(AddressDto billingAddress) { this.billingAddress = billingAddress; }

    public AddressDto getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(AddressDto shippingAddress) { this.shippingAddress = shippingAddress; }

    public Boolean getShippingSameAsBilling() { return shippingSameAsBilling; }
    public void setShippingSameAsBilling(Boolean shippingSameAsBilling) { this.shippingSameAsBilling = shippingSameAsBilling; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getSetupCostChf() { return setupCostChf; }
    public void setSetupCostChf(BigDecimal setupCostChf) { this.setupCostChf = setupCostChf; }

    public BigDecimal getShippingCostChf() { return shippingCostChf; }
    public void setShippingCostChf(BigDecimal shippingCostChf) { this.shippingCostChf = shippingCostChf; }

    public BigDecimal getDiscountChf() { return discountChf; }
    public void setDiscountChf(BigDecimal discountChf) { this.discountChf = discountChf; }

    public BigDecimal getSubtotalChf() { return subtotalChf; }
    public void setSubtotalChf(BigDecimal subtotalChf) { this.subtotalChf = subtotalChf; }

    public Boolean getIsCadOrder() { return isCadOrder; }
    public void setIsCadOrder(Boolean isCadOrder) { this.isCadOrder = isCadOrder; }

    public UUID getSourceRequestId() { return sourceRequestId; }
    public void setSourceRequestId(UUID sourceRequestId) { this.sourceRequestId = sourceRequestId; }

    public BigDecimal getCadHours() { return cadHours; }
    public void setCadHours(BigDecimal cadHours) { this.cadHours = cadHours; }

    public BigDecimal getCadHourlyRateChf() { return cadHourlyRateChf; }
    public void setCadHourlyRateChf(BigDecimal cadHourlyRateChf) { this.cadHourlyRateChf = cadHourlyRateChf; }

    public BigDecimal getCadTotalChf() { return cadTotalChf; }
    public void setCadTotalChf(BigDecimal cadTotalChf) { this.cadTotalChf = cadTotalChf; }

    public Integer getCadFileCount() { return cadFileCount; }
    public void setCadFileCount(Integer cadFileCount) { this.cadFileCount = cadFileCount; }

    public Boolean getCadFileDownloadAvailable() { return cadFileDownloadAvailable; }
    public void setCadFileDownloadAvailable(Boolean cadFileDownloadAvailable) { this.cadFileDownloadAvailable = cadFileDownloadAvailable; }

    public List<OrderDeliverableFileDto> getCadFiles() { return cadFiles; }
    public void setCadFiles(List<OrderDeliverableFileDto> cadFiles) { this.cadFiles = cadFiles; }

    public BigDecimal getTotalChf() { return totalChf; }
    public void setTotalChf(BigDecimal totalChf) { this.totalChf = totalChf; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(OffsetDateTime paidAt) { this.paidAt = paidAt; }

    public String getPrintMaterialCode() { return printMaterialCode; }
    public void setPrintMaterialCode(String printMaterialCode) { this.printMaterialCode = printMaterialCode; }

    public BigDecimal getPrintNozzleDiameterMm() { return printNozzleDiameterMm; }
    public void setPrintNozzleDiameterMm(BigDecimal printNozzleDiameterMm) { this.printNozzleDiameterMm = printNozzleDiameterMm; }

    public BigDecimal getPrintLayerHeightMm() { return printLayerHeightMm; }
    public void setPrintLayerHeightMm(BigDecimal printLayerHeightMm) { this.printLayerHeightMm = printLayerHeightMm; }

    public String getPrintInfillPattern() { return printInfillPattern; }
    public void setPrintInfillPattern(String printInfillPattern) { this.printInfillPattern = printInfillPattern; }

    public Integer getPrintInfillPercent() { return printInfillPercent; }
    public void setPrintInfillPercent(Integer printInfillPercent) { this.printInfillPercent = printInfillPercent; }

    public Boolean getPrintSupportsEnabled() { return printSupportsEnabled; }
    public void setPrintSupportsEnabled(Boolean printSupportsEnabled) { this.printSupportsEnabled = printSupportsEnabled; }

    public List<AdminEmailLogDto> getEmailLogs() { return emailLogs; }
    public void setEmailLogs(List<AdminEmailLogDto> emailLogs) { this.emailLogs = emailLogs; }

    public List<OrderItemDto> getItems() { return items; }
    public void setItems(List<OrderItemDto> items) { this.items = items; }
}
