package com.printcalculator.dto;

public class PrintSettingsDto {
    // Mode: "BASIC" or "ADVANCED"
    private String complexityMode;
    
    // Common
    private String material; // e.g. "PLA", "PLA TOUGH", "PETG"
    private String color;    // e.g. "White", "#FFFFFF"
    private Integer quantity;
    private Long filamentVariantId;
    private Long printerMachineId;
    
    // Basic Mode
    private String quality;  // "draft", "standard", "high"
    
    // Advanced Mode (Optional in Basic)
    private Double nozzleDiameter;
    private Double layerHeight;
    private Double infillDensity;
    private String infillPattern;
    private Boolean supportsEnabled;
    private Boolean allowSplitForOversized;
    private String notes;

    // Dimensions
    private Double boundingBoxX;
    private Double boundingBoxY;
    private Double boundingBoxZ;

    public String getComplexityMode() {
        return complexityMode;
    }

    public void setComplexityMode(String complexityMode) {
        this.complexityMode = complexityMode;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Long getFilamentVariantId() {
        return filamentVariantId;
    }

    public void setFilamentVariantId(Long filamentVariantId) {
        this.filamentVariantId = filamentVariantId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Long getPrinterMachineId() {
        return printerMachineId;
    }

    public void setPrinterMachineId(Long printerMachineId) {
        this.printerMachineId = printerMachineId;
    }

    public String getQuality() {
        return quality;
    }

    public void setQuality(String quality) {
        this.quality = quality;
    }

    public Double getNozzleDiameter() {
        return nozzleDiameter;
    }

    public void setNozzleDiameter(Double nozzleDiameter) {
        this.nozzleDiameter = nozzleDiameter;
    }

    public Double getLayerHeight() {
        return layerHeight;
    }

    public void setLayerHeight(Double layerHeight) {
        this.layerHeight = layerHeight;
    }

    public Double getInfillDensity() {
        return infillDensity;
    }

    public void setInfillDensity(Double infillDensity) {
        this.infillDensity = infillDensity;
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

    public Boolean getAllowSplitForOversized() {
        return allowSplitForOversized;
    }

    public void setAllowSplitForOversized(Boolean allowSplitForOversized) {
        this.allowSplitForOversized = allowSplitForOversized;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Double getBoundingBoxX() {
        return boundingBoxX;
    }

    public void setBoundingBoxX(Double boundingBoxX) {
        this.boundingBoxX = boundingBoxX;
    }

    public Double getBoundingBoxY() {
        return boundingBoxY;
    }

    public void setBoundingBoxY(Double boundingBoxY) {
        this.boundingBoxY = boundingBoxY;
    }

    public Double getBoundingBoxZ() {
        return boundingBoxZ;
    }

    public void setBoundingBoxZ(Double boundingBoxZ) {
        this.boundingBoxZ = boundingBoxZ;
    }
}
