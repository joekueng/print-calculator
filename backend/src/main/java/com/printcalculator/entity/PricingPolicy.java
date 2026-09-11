package com.printcalculator.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "pricing_policy")
public class PricingPolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pricing_policy_id", nullable = false)
    private Long id;

    @Column(name = "policy_name", nullable = false, length = Integer.MAX_VALUE)
    private String policyName;

    @Column(name = "valid_from", nullable = false)
    private OffsetDateTime validFrom;

    @Column(name = "valid_to")
    private OffsetDateTime validTo;

    @Column(name = "electricity_cost_chf_per_kwh", nullable = false, precision = 10, scale = 6)
    private BigDecimal electricityCostChfPerKwh;

    @ColumnDefault("20.000")
    @Column(name = "markup_percent", nullable = false, precision = 6, scale = 3)
    private BigDecimal markupPercent;

    @ColumnDefault("0.00")
    @Column(name = "fixed_job_fee_chf", nullable = false, precision = 10, scale = 2)
    private BigDecimal fixedJobFeeChf;

    @ColumnDefault("10.00")
    @Column(name = "split_model_setup_fee_chf", precision = 10, scale = 2)
    private BigDecimal splitModelSetupFeeChf;

    @ColumnDefault("0.00")
    @Column(name = "nozzle_change_base_fee_chf", nullable = false, precision = 10, scale = 2)
    private BigDecimal nozzleChangeBaseFeeChf;

    @ColumnDefault("0.00")
    @Column(name = "cad_cost_chf_per_hour", nullable = false, precision = 10, scale = 2)
    private BigDecimal cadCostChfPerHour;

    @ColumnDefault("true")
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPolicyName() {
        return policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }

    public OffsetDateTime getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(OffsetDateTime validFrom) {
        this.validFrom = validFrom;
    }

    public OffsetDateTime getValidTo() {
        return validTo;
    }

    public void setValidTo(OffsetDateTime validTo) {
        this.validTo = validTo;
    }

    public BigDecimal getElectricityCostChfPerKwh() {
        return electricityCostChfPerKwh;
    }

    public void setElectricityCostChfPerKwh(BigDecimal electricityCostChfPerKwh) {
        this.electricityCostChfPerKwh = electricityCostChfPerKwh;
    }

    public BigDecimal getMarkupPercent() {
        return markupPercent;
    }

    public void setMarkupPercent(BigDecimal markupPercent) {
        this.markupPercent = markupPercent;
    }

    public BigDecimal getFixedJobFeeChf() {
        return fixedJobFeeChf;
    }

    public void setFixedJobFeeChf(BigDecimal fixedJobFeeChf) {
        this.fixedJobFeeChf = fixedJobFeeChf;
    }

    public BigDecimal getSplitModelSetupFeeChf() {
        return splitModelSetupFeeChf;
    }

    public void setSplitModelSetupFeeChf(BigDecimal splitModelSetupFeeChf) {
        this.splitModelSetupFeeChf = splitModelSetupFeeChf;
    }

    public BigDecimal getNozzleChangeBaseFeeChf() {
        return nozzleChangeBaseFeeChf;
    }

    public void setNozzleChangeBaseFeeChf(BigDecimal nozzleChangeBaseFeeChf) {
        this.nozzleChangeBaseFeeChf = nozzleChangeBaseFeeChf;
    }

    public BigDecimal getCadCostChfPerHour() {
        return cadCostChfPerHour;
    }

    public void setCadCostChfPerHour(BigDecimal cadCostChfPerHour) {
        this.cadCostChfPerHour = cadCostChfPerHour;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

}
