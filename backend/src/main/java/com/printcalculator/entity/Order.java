package com.printcalculator.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "orders", indexes = {@Index(name = "ix_orders_status",
        columnList = "status")})
public class Order {
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "shipping_quote_snapshot")
    private java.util.Map<String, Object> shippingQuoteSnapshot;

    public java.util.Map<String, Object> getShippingQuoteSnapshot() { return shippingQuoteSnapshot; }
    public void setShippingQuoteSnapshot(java.util.Map<String, Object> value) { shippingQuoteSnapshot = value; }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "order_id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_quote_session_id")
    private QuoteSession sourceQuoteSession;

    @ColumnDefault("'CALCULATOR'")
    @Column(name = "source_type", nullable = false, length = Integer.MAX_VALUE)
    private String sourceType;

    @Column(name = "status", nullable = false, length = Integer.MAX_VALUE)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "customer_email", nullable = false, length = Integer.MAX_VALUE)
    private String customerEmail;

    @Column(name = "customer_phone", length = Integer.MAX_VALUE)
    private String customerPhone;

    @Column(name = "billing_customer_type", nullable = false, length = Integer.MAX_VALUE)
    private String billingCustomerType;

    @Column(name = "billing_first_name", length = Integer.MAX_VALUE)
    private String billingFirstName;

    @Column(name = "billing_last_name", length = Integer.MAX_VALUE)
    private String billingLastName;

    @Column(name = "billing_company_name", length = Integer.MAX_VALUE)
    private String billingCompanyName;

    @Column(name = "billing_contact_person", length = Integer.MAX_VALUE)
    private String billingContactPerson;

    @Column(name = "billing_address_line1", nullable = false, length = Integer.MAX_VALUE)
    private String billingAddressLine1;

    @Column(name = "billing_address_line2", length = Integer.MAX_VALUE)
    private String billingAddressLine2;

    @Column(name = "billing_zip", nullable = false, length = Integer.MAX_VALUE)
    private String billingZip;

    @Column(name = "billing_city", nullable = false, length = Integer.MAX_VALUE)
    private String billingCity;

    @ColumnDefault("'CH'")
    @Column(name = "billing_country_code", nullable = false, length = 2)
    private String billingCountryCode;

    @ColumnDefault("true")
    @Column(name = "shipping_same_as_billing", nullable = false)
    private Boolean shippingSameAsBilling;

    @Column(name = "shipping_first_name", length = Integer.MAX_VALUE)
    private String shippingFirstName;

    @Column(name = "shipping_last_name", length = Integer.MAX_VALUE)
    private String shippingLastName;

    @Column(name = "shipping_company_name", length = Integer.MAX_VALUE)
    private String shippingCompanyName;

    @Column(name = "shipping_contact_person", length = Integer.MAX_VALUE)
    private String shippingContactPerson;

    @Column(name = "shipping_address_line1", length = Integer.MAX_VALUE)
    private String shippingAddressLine1;

    @Column(name = "shipping_address_line2", length = Integer.MAX_VALUE)
    private String shippingAddressLine2;

    @Column(name = "shipping_zip", length = Integer.MAX_VALUE)
    private String shippingZip;

    @Column(name = "shipping_city", length = Integer.MAX_VALUE)
    private String shippingCity;

    @Column(name = "shipping_country_code", length = 2)
    private String shippingCountryCode;

    @ColumnDefault("'it'")
    @Column(name = "preferred_language", length = 2)
    private String preferredLanguage;

    @ColumnDefault("'CHF'")
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @ColumnDefault("0.00")
    @Column(name = "setup_cost_chf", nullable = false, precision = 12, scale = 2)
    private BigDecimal setupCostChf;

    @ColumnDefault("0.00")
    @Column(name = "shipping_cost_chf", nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingCostChf;

    @ColumnDefault("0.00")
    @Column(name = "discount_chf", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountChf;

    @ColumnDefault("0.00")
    @Column(name = "subtotal_chf", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotalChf;

    @ColumnDefault("false")
    @Column(name = "is_cad_order", nullable = false)
    private Boolean isCadOrder;

    @Column(name = "source_request_id")
    private UUID sourceRequestId;

    @Column(name = "cad_hours", precision = 10, scale = 2)
    private BigDecimal cadHours;

    @Column(name = "cad_hourly_rate_chf", precision = 10, scale = 2)
    private BigDecimal cadHourlyRateChf;

    @ColumnDefault("0.00")
    @Column(name = "cad_total_chf", nullable = false, precision = 12, scale = 2)
    private BigDecimal cadTotalChf;

    @ColumnDefault("0.00")
    @Column(name = "total_chf", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalChf;

    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @ColumnDefault("now()")
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @PrePersist
    private void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (shippingSameAsBilling == null) {
            shippingSameAsBilling = true;
        }
        if (sourceType == null || sourceType.isBlank()) {
            sourceType = "CALCULATOR";
        }
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = OffsetDateTime.now();
        if (shippingSameAsBilling == null) {
            shippingSameAsBilling = true;
        }
        if (sourceType == null || sourceType.isBlank()) {
            sourceType = "CALCULATOR";
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    @Transient
    public String getOrderNumber() {
        if (id == null) {
            return null;
        }
        String rawId = id.toString();
        int dashIndex = rawId.indexOf('-');
        return dashIndex > 0 ? rawId.substring(0, dashIndex) : rawId;
    }

    public QuoteSession getSourceQuoteSession() {
        return sourceQuoteSession;
    }

    public void setSourceQuoteSession(QuoteSession sourceQuoteSession) {
        this.sourceQuoteSession = sourceQuoteSession;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getBillingCustomerType() {
        return billingCustomerType;
    }

    public void setBillingCustomerType(String billingCustomerType) {
        this.billingCustomerType = billingCustomerType;
    }

    public String getBillingFirstName() {
        return billingFirstName;
    }

    public void setBillingFirstName(String billingFirstName) {
        this.billingFirstName = billingFirstName;
    }

    public String getBillingLastName() {
        return billingLastName;
    }

    public void setBillingLastName(String billingLastName) {
        this.billingLastName = billingLastName;
    }

    public String getBillingCompanyName() {
        return billingCompanyName;
    }

    public void setBillingCompanyName(String billingCompanyName) {
        this.billingCompanyName = billingCompanyName;
    }

    public String getBillingContactPerson() {
        return billingContactPerson;
    }

    public void setBillingContactPerson(String billingContactPerson) {
        this.billingContactPerson = billingContactPerson;
    }

    public String getBillingAddressLine1() {
        return billingAddressLine1;
    }

    public void setBillingAddressLine1(String billingAddressLine1) {
        this.billingAddressLine1 = billingAddressLine1;
    }

    public String getBillingAddressLine2() {
        return billingAddressLine2;
    }

    public void setBillingAddressLine2(String billingAddressLine2) {
        this.billingAddressLine2 = billingAddressLine2;
    }

    public String getBillingZip() {
        return billingZip;
    }

    public void setBillingZip(String billingZip) {
        this.billingZip = billingZip;
    }

    public String getBillingCity() {
        return billingCity;
    }

    public void setBillingCity(String billingCity) {
        this.billingCity = billingCity;
    }

    public String getBillingCountryCode() {
        return billingCountryCode;
    }

    public void setBillingCountryCode(String billingCountryCode) {
        this.billingCountryCode = billingCountryCode;
    }

    public Boolean getShippingSameAsBilling() {
        return shippingSameAsBilling;
    }

    public void setShippingSameAsBilling(Boolean shippingSameAsBilling) {
        this.shippingSameAsBilling = shippingSameAsBilling;
    }

    public String getShippingFirstName() {
        return shippingFirstName;
    }

    public void setShippingFirstName(String shippingFirstName) {
        this.shippingFirstName = shippingFirstName;
    }

    public String getShippingLastName() {
        return shippingLastName;
    }

    public void setShippingLastName(String shippingLastName) {
        this.shippingLastName = shippingLastName;
    }

    public String getShippingCompanyName() {
        return shippingCompanyName;
    }

    public void setShippingCompanyName(String shippingCompanyName) {
        this.shippingCompanyName = shippingCompanyName;
    }

    public String getShippingContactPerson() {
        return shippingContactPerson;
    }

    public void setShippingContactPerson(String shippingContactPerson) {
        this.shippingContactPerson = shippingContactPerson;
    }

    public String getShippingAddressLine1() {
        return shippingAddressLine1;
    }

    public void setShippingAddressLine1(String shippingAddressLine1) {
        this.shippingAddressLine1 = shippingAddressLine1;
    }

    public String getShippingAddressLine2() {
        return shippingAddressLine2;
    }

    public void setShippingAddressLine2(String shippingAddressLine2) {
        this.shippingAddressLine2 = shippingAddressLine2;
    }

    public String getShippingZip() {
        return shippingZip;
    }

    public void setShippingZip(String shippingZip) {
        this.shippingZip = shippingZip;
    }

    public String getShippingCity() {
        return shippingCity;
    }

    public void setShippingCity(String shippingCity) {
        this.shippingCity = shippingCity;
    }

    public String getShippingCountryCode() {
        return shippingCountryCode;
    }

    public void setShippingCountryCode(String shippingCountryCode) {
        this.shippingCountryCode = shippingCountryCode;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }

    public BigDecimal getSetupCostChf() {
        return setupCostChf;
    }

    public void setSetupCostChf(BigDecimal setupCostChf) {
        this.setupCostChf = setupCostChf;
    }

    public BigDecimal getShippingCostChf() {
        return shippingCostChf;
    }

    public void setShippingCostChf(BigDecimal shippingCostChf) {
        this.shippingCostChf = shippingCostChf;
    }

    public BigDecimal getDiscountChf() {
        return discountChf;
    }

    public void setDiscountChf(BigDecimal discountChf) {
        this.discountChf = discountChf;
    }

    public BigDecimal getSubtotalChf() {
        return subtotalChf;
    }

    public void setSubtotalChf(BigDecimal subtotalChf) {
        this.subtotalChf = subtotalChf;
    }

    public Boolean getIsCadOrder() {
        return isCadOrder;
    }

    public void setIsCadOrder(Boolean isCadOrder) {
        this.isCadOrder = isCadOrder;
    }

    public UUID getSourceRequestId() {
        return sourceRequestId;
    }

    public void setSourceRequestId(UUID sourceRequestId) {
        this.sourceRequestId = sourceRequestId;
    }

    public BigDecimal getCadHours() {
        return cadHours;
    }

    public void setCadHours(BigDecimal cadHours) {
        this.cadHours = cadHours;
    }

    public BigDecimal getCadHourlyRateChf() {
        return cadHourlyRateChf;
    }

    public void setCadHourlyRateChf(BigDecimal cadHourlyRateChf) {
        this.cadHourlyRateChf = cadHourlyRateChf;
    }

    public BigDecimal getCadTotalChf() {
        return cadTotalChf;
    }

    public void setCadTotalChf(BigDecimal cadTotalChf) {
        this.cadTotalChf = cadTotalChf;
    }

    public BigDecimal getTotalChf() {
        return totalChf;
    }

    public void setTotalChf(BigDecimal totalChf) {
        this.totalChf = totalChf;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public OffsetDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(OffsetDateTime paidAt) {
        this.paidAt = paidAt;
    }

}
