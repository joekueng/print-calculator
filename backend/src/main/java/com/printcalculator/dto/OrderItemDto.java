package com.printcalculator.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class OrderItemDto {
    private UUID id;
    private String itemType;
    private String originalFilename;
    private String displayName;
    private String materialCode;
    private String colorCode;
    private Long filamentVariantId;
    private UUID shopProductId;
    private UUID shopProductVariantId;
    private String shopProductSlug;
    private String shopProductName;
    private String shopVariantLabel;
    private String shopVariantColorName;
    private String shopVariantColorLabelIt;
    private String shopVariantColorLabelEn;
    private String shopVariantColorLabelDe;
    private String shopVariantColorLabelFr;
    private String shopVariantColorHex;
    private String filamentVariantDisplayName;
    private String filamentColorName;
    private String filamentColorLabelIt;
    private String filamentColorLabelEn;
    private String filamentColorLabelDe;
    private String filamentColorLabelFr;
    private String filamentColorHex;
    private String quality;
    private BigDecimal nozzleDiameterMm;
    private BigDecimal layerHeightMm;
    private Integer infillPercent;
    private String infillPattern;
    private Boolean supportsEnabled;
    private Boolean requiresSplitPrinting;
    private Integer quantity;
    private Integer printTimeSeconds;
    private BigDecimal materialGrams;
    private BigDecimal unitPriceChf;
    private BigDecimal lineTotalChf;

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }

    public String getColorCode() { return colorCode; }
    public void setColorCode(String colorCode) { this.colorCode = colorCode; }

    public Long getFilamentVariantId() { return filamentVariantId; }
    public void setFilamentVariantId(Long filamentVariantId) { this.filamentVariantId = filamentVariantId; }

    public UUID getShopProductId() { return shopProductId; }
    public void setShopProductId(UUID shopProductId) { this.shopProductId = shopProductId; }

    public UUID getShopProductVariantId() { return shopProductVariantId; }
    public void setShopProductVariantId(UUID shopProductVariantId) { this.shopProductVariantId = shopProductVariantId; }

    public String getShopProductSlug() { return shopProductSlug; }
    public void setShopProductSlug(String shopProductSlug) { this.shopProductSlug = shopProductSlug; }

    public String getShopProductName() { return shopProductName; }
    public void setShopProductName(String shopProductName) { this.shopProductName = shopProductName; }

    public String getShopVariantLabel() { return shopVariantLabel; }
    public void setShopVariantLabel(String shopVariantLabel) { this.shopVariantLabel = shopVariantLabel; }

    public String getShopVariantColorName() { return shopVariantColorName; }
    public void setShopVariantColorName(String shopVariantColorName) { this.shopVariantColorName = shopVariantColorName; }

    public String getShopVariantColorLabelIt() { return shopVariantColorLabelIt; }
    public void setShopVariantColorLabelIt(String shopVariantColorLabelIt) { this.shopVariantColorLabelIt = shopVariantColorLabelIt; }

    public String getShopVariantColorLabelEn() { return shopVariantColorLabelEn; }
    public void setShopVariantColorLabelEn(String shopVariantColorLabelEn) { this.shopVariantColorLabelEn = shopVariantColorLabelEn; }

    public String getShopVariantColorLabelDe() { return shopVariantColorLabelDe; }
    public void setShopVariantColorLabelDe(String shopVariantColorLabelDe) { this.shopVariantColorLabelDe = shopVariantColorLabelDe; }

    public String getShopVariantColorLabelFr() { return shopVariantColorLabelFr; }
    public void setShopVariantColorLabelFr(String shopVariantColorLabelFr) { this.shopVariantColorLabelFr = shopVariantColorLabelFr; }

    public String getShopVariantColorHex() { return shopVariantColorHex; }
    public void setShopVariantColorHex(String shopVariantColorHex) { this.shopVariantColorHex = shopVariantColorHex; }

    public String getFilamentVariantDisplayName() { return filamentVariantDisplayName; }
    public void setFilamentVariantDisplayName(String filamentVariantDisplayName) { this.filamentVariantDisplayName = filamentVariantDisplayName; }

    public String getFilamentColorName() { return filamentColorName; }
    public void setFilamentColorName(String filamentColorName) { this.filamentColorName = filamentColorName; }

    public String getFilamentColorLabelIt() { return filamentColorLabelIt; }
    public void setFilamentColorLabelIt(String filamentColorLabelIt) { this.filamentColorLabelIt = filamentColorLabelIt; }

    public String getFilamentColorLabelEn() { return filamentColorLabelEn; }
    public void setFilamentColorLabelEn(String filamentColorLabelEn) { this.filamentColorLabelEn = filamentColorLabelEn; }

    public String getFilamentColorLabelDe() { return filamentColorLabelDe; }
    public void setFilamentColorLabelDe(String filamentColorLabelDe) { this.filamentColorLabelDe = filamentColorLabelDe; }

    public String getFilamentColorLabelFr() { return filamentColorLabelFr; }
    public void setFilamentColorLabelFr(String filamentColorLabelFr) { this.filamentColorLabelFr = filamentColorLabelFr; }

    public String getFilamentColorHex() { return filamentColorHex; }
    public void setFilamentColorHex(String filamentColorHex) { this.filamentColorHex = filamentColorHex; }

    public String getQuality() { return quality; }
    public void setQuality(String quality) { this.quality = quality; }

    public BigDecimal getNozzleDiameterMm() { return nozzleDiameterMm; }
    public void setNozzleDiameterMm(BigDecimal nozzleDiameterMm) { this.nozzleDiameterMm = nozzleDiameterMm; }

    public BigDecimal getLayerHeightMm() { return layerHeightMm; }
    public void setLayerHeightMm(BigDecimal layerHeightMm) { this.layerHeightMm = layerHeightMm; }

    public Integer getInfillPercent() { return infillPercent; }
    public void setInfillPercent(Integer infillPercent) { this.infillPercent = infillPercent; }

    public String getInfillPattern() { return infillPattern; }
    public void setInfillPattern(String infillPattern) { this.infillPattern = infillPattern; }

    public Boolean getSupportsEnabled() { return supportsEnabled; }
    public void setSupportsEnabled(Boolean supportsEnabled) { this.supportsEnabled = supportsEnabled; }

    public Boolean getRequiresSplitPrinting() { return requiresSplitPrinting; }
    public void setRequiresSplitPrinting(Boolean requiresSplitPrinting) { this.requiresSplitPrinting = requiresSplitPrinting; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Integer getPrintTimeSeconds() { return printTimeSeconds; }
    public void setPrintTimeSeconds(Integer printTimeSeconds) { this.printTimeSeconds = printTimeSeconds; }

    public BigDecimal getMaterialGrams() { return materialGrams; }
    public void setMaterialGrams(BigDecimal materialGrams) { this.materialGrams = materialGrams; }

    public BigDecimal getUnitPriceChf() { return unitPriceChf; }
    public void setUnitPriceChf(BigDecimal unitPriceChf) { this.unitPriceChf = unitPriceChf; }

    public BigDecimal getLineTotalChf() { return lineTotalChf; }
    public void setLineTotalChf(BigDecimal lineTotalChf) { this.lineTotalChf = lineTotalChf; }
}
