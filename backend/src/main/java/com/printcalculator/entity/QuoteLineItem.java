package com.printcalculator.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "quote_line_items", indexes = {@Index(name = "ix_quote_line_items_session",
        columnList = "quote_session_id")})
public class QuoteLineItem {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "quote_line_item_id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "quote_session_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private QuoteSession quoteSession;

    @Column(name = "status", nullable = false, length = Integer.MAX_VALUE)
    private String status;

    @ColumnDefault("'PRINT_FILE'")
    @Column(name = "line_item_type", nullable = false, length = Integer.MAX_VALUE)
    private String lineItemType;

    @Column(name = "original_filename", nullable = false, length = Integer.MAX_VALUE)
    private String originalFilename;

    @Column(name = "display_name", length = Integer.MAX_VALUE)
    private String displayName;

    @ColumnDefault("1")
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "color_code", length = Integer.MAX_VALUE)
    private String colorCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filament_variant_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private FilamentVariant filamentVariant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_product_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private ShopProduct shopProduct;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_product_variant_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private ShopProductVariant shopProductVariant;

    @Column(name = "shop_product_slug", length = Integer.MAX_VALUE)
    private String shopProductSlug;

    @Column(name = "shop_product_name", length = Integer.MAX_VALUE)
    private String shopProductName;

    @Column(name = "shop_variant_label", length = Integer.MAX_VALUE)
    private String shopVariantLabel;

    @Column(name = "shop_variant_color_name", length = Integer.MAX_VALUE)
    private String shopVariantColorName;

    @Column(name = "shop_variant_color_hex", length = Integer.MAX_VALUE)
    private String shopVariantColorHex;

    @Column(name = "material_code", length = Integer.MAX_VALUE)
    private String materialCode;

    @Column(name = "quality", length = Integer.MAX_VALUE)
    private String quality;

    @Column(name = "nozzle_diameter_mm", precision = 5, scale = 2)
    private BigDecimal nozzleDiameterMm;

    @Column(name = "layer_height_mm", precision = 6, scale = 3)
    private BigDecimal layerHeightMm;

    @Column(name = "infill_percent")
    private Integer infillPercent;

    @Column(name = "infill_pattern", length = Integer.MAX_VALUE)
    private String infillPattern;

    @Column(name = "supports_enabled")
    private Boolean supportsEnabled;

    @ColumnDefault("false")
    @Column(name = "requires_split_printing")
    private Boolean requiresSplitPrinting = false;

    @Column(name = "bounding_box_x_mm", precision = 10, scale = 3)
    private BigDecimal boundingBoxXMm;

    @Column(name = "bounding_box_y_mm", precision = 10, scale = 3)
    private BigDecimal boundingBoxYMm;

    @Column(name = "bounding_box_z_mm", precision = 10, scale = 3)
    private BigDecimal boundingBoxZMm;

    @Column(name = "print_time_seconds")
    private Integer printTimeSeconds;

    @Column(name = "material_grams", precision = 12, scale = 2)
    private BigDecimal materialGrams;

    @Column(name = "unit_price_chf", precision = 12, scale = 2)
    private BigDecimal unitPriceChf;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pricing_breakdown")
    private Map<String, Object> pricingBreakdown;

    @Column(name = "error_message", length = Integer.MAX_VALUE)
    private String errorMessage;

    @Column(name = "stored_path", length = Integer.MAX_VALUE)
    private String storedPath;

    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @ColumnDefault("now()")
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    private void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (quantity == null) {
            quantity = 1;
        }
        if (lineItemType == null || lineItemType.isBlank()) {
            lineItemType = "PRINT_FILE";
        }
        if (requiresSplitPrinting == null) {
            requiresSplitPrinting = false;
        }
        if ((displayName == null || displayName.isBlank()) && originalFilename != null && !originalFilename.isBlank()) {
            displayName = originalFilename;
        } else if ((displayName == null || displayName.isBlank()) && shopProductName != null && !shopProductName.isBlank()) {
            displayName = shopProductName;
        }
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = OffsetDateTime.now();
        if (lineItemType == null || lineItemType.isBlank()) {
            lineItemType = "PRINT_FILE";
        }
        if (requiresSplitPrinting == null) {
            requiresSplitPrinting = false;
        }
        if ((displayName == null || displayName.isBlank()) && originalFilename != null && !originalFilename.isBlank()) {
            displayName = originalFilename;
        } else if ((displayName == null || displayName.isBlank()) && shopProductName != null && !shopProductName.isBlank()) {
            displayName = shopProductName;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public QuoteSession getQuoteSession() {
        return quoteSession;
    }

    public void setQuoteSession(QuoteSession quoteSession) {
        this.quoteSession = quoteSession;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLineItemType() {
        return lineItemType;
    }

    public void setLineItemType(String lineItemType) {
        this.lineItemType = lineItemType;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getColorCode() {
        return colorCode;
    }

    public void setColorCode(String colorCode) {
        this.colorCode = colorCode;
    }

    public FilamentVariant getFilamentVariant() {
        return filamentVariant;
    }

    public void setFilamentVariant(FilamentVariant filamentVariant) {
        this.filamentVariant = filamentVariant;
    }

    public ShopProduct getShopProduct() {
        return shopProduct;
    }

    public void setShopProduct(ShopProduct shopProduct) {
        this.shopProduct = shopProduct;
    }

    public ShopProductVariant getShopProductVariant() {
        return shopProductVariant;
    }

    public void setShopProductVariant(ShopProductVariant shopProductVariant) {
        this.shopProductVariant = shopProductVariant;
    }

    public String getShopProductSlug() {
        return shopProductSlug;
    }

    public void setShopProductSlug(String shopProductSlug) {
        this.shopProductSlug = shopProductSlug;
    }

    public String getShopProductName() {
        return shopProductName;
    }

    public void setShopProductName(String shopProductName) {
        this.shopProductName = shopProductName;
    }

    public String getShopVariantLabel() {
        return shopVariantLabel;
    }

    public void setShopVariantLabel(String shopVariantLabel) {
        this.shopVariantLabel = shopVariantLabel;
    }

    public String getShopVariantColorName() {
        return shopVariantColorName;
    }

    public void setShopVariantColorName(String shopVariantColorName) {
        this.shopVariantColorName = shopVariantColorName;
    }

    public String getShopVariantColorHex() {
        return shopVariantColorHex;
    }

    public void setShopVariantColorHex(String shopVariantColorHex) {
        this.shopVariantColorHex = shopVariantColorHex;
    }

    public String getMaterialCode() {
        return materialCode;
    }

    public void setMaterialCode(String materialCode) {
        this.materialCode = materialCode;
    }

    public String getQuality() {
        return quality;
    }

    public void setQuality(String quality) {
        this.quality = quality;
    }

    public BigDecimal getNozzleDiameterMm() {
        return nozzleDiameterMm;
    }

    public void setNozzleDiameterMm(BigDecimal nozzleDiameterMm) {
        this.nozzleDiameterMm = nozzleDiameterMm;
    }

    public BigDecimal getLayerHeightMm() {
        return layerHeightMm;
    }

    public void setLayerHeightMm(BigDecimal layerHeightMm) {
        this.layerHeightMm = layerHeightMm;
    }

    public Integer getInfillPercent() {
        return infillPercent;
    }

    public void setInfillPercent(Integer infillPercent) {
        this.infillPercent = infillPercent;
    }

    public String getInfillPattern() {
        return infillPattern;
    }

    public void setInfillPattern(String infillPattern) {
        this.infillPattern = infillPattern;
    }

    public Boolean getSupportsEnabled() {
        return supportsEnabled;
    }

    public void setSupportsEnabled(Boolean supportsEnabled) {
        this.supportsEnabled = supportsEnabled;
    }

    public Boolean getRequiresSplitPrinting() {
        return requiresSplitPrinting;
    }

    public void setRequiresSplitPrinting(Boolean requiresSplitPrinting) {
        this.requiresSplitPrinting = requiresSplitPrinting;
    }

    public BigDecimal getBoundingBoxXMm() {
        return boundingBoxXMm;
    }

    public void setBoundingBoxXMm(BigDecimal boundingBoxXMm) {
        this.boundingBoxXMm = boundingBoxXMm;
    }

    public BigDecimal getBoundingBoxYMm() {
        return boundingBoxYMm;
    }

    public void setBoundingBoxYMm(BigDecimal boundingBoxYMm) {
        this.boundingBoxYMm = boundingBoxYMm;
    }

    public BigDecimal getBoundingBoxZMm() {
        return boundingBoxZMm;
    }

    public void setBoundingBoxZMm(BigDecimal boundingBoxZMm) {
        this.boundingBoxZMm = boundingBoxZMm;
    }

    public Integer getPrintTimeSeconds() {
        return printTimeSeconds;
    }

    public void setPrintTimeSeconds(Integer printTimeSeconds) {
        this.printTimeSeconds = printTimeSeconds;
    }

    public BigDecimal getMaterialGrams() {
        return materialGrams;
    }

    public void setMaterialGrams(BigDecimal materialGrams) {
        this.materialGrams = materialGrams;
    }

    public BigDecimal getUnitPriceChf() {
        return unitPriceChf;
    }

    public void setUnitPriceChf(BigDecimal unitPriceChf) {
        this.unitPriceChf = unitPriceChf;
    }

    public Map<String, Object> getPricingBreakdown() {
        return pricingBreakdown;
    }

    public void setPricingBreakdown(Map<String, Object> pricingBreakdown) {
        this.pricingBreakdown = pricingBreakdown;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getStoredPath() {
        return storedPath;
    }

    public void setStoredPath(String storedPath) {
        this.storedPath = storedPath;
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

}
