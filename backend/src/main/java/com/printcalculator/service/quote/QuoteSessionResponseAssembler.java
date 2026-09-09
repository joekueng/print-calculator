package com.printcalculator.service.quote;

import com.printcalculator.entity.QuoteLineItem;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.service.QuoteSessionTotalsService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class QuoteSessionResponseAssembler {
    private final QuoteStorageService quoteStorageService;

    public QuoteSessionResponseAssembler(QuoteStorageService quoteStorageService) {
        this.quoteStorageService = quoteStorageService;
    }

    public Map<String, Object> assemble(QuoteSession session,
                                        List<QuoteLineItem> items,
                                        QuoteSessionTotalsService.QuoteSessionTotals totals) {
        List<Map<String, Object>> itemsDto = new ArrayList<>();
        for (QuoteLineItem item : items) {
            itemsDto.add(toItemDto(item, totals));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("session", session);
        response.put("items", itemsDto);
        response.put("printItemsTotalChf", totals.printItemsTotalChf());
        response.put("cadTotalChf", totals.cadTotalChf());
        response.put("itemsTotalChf", totals.itemsTotalChf());
        response.put("baseSetupCostChf", totals.baseSetupCostChf());
        response.put("nozzleChangeCostChf", totals.nozzleChangeCostChf());
        response.put("setupCostChf", totals.setupCostChf());
        response.put("shippingCostChf", totals.shippingCostChf());
        response.put("shippingQuote", totals.shippingQuote());
        response.put("globalMachineCostChf", totals.globalMachineCostChf());
        response.put("grandTotalChf", totals.grandTotalChf());
        return response;
    }

    public Map<String, Object> emptyCart() {
        Map<String, Object> response = new HashMap<>();
        response.put("session", null);
        response.put("items", List.of());
        response.put("printItemsTotalChf", BigDecimal.ZERO);
        response.put("cadTotalChf", BigDecimal.ZERO);
        response.put("itemsTotalChf", BigDecimal.ZERO);
        response.put("baseSetupCostChf", BigDecimal.ZERO);
        response.put("nozzleChangeCostChf", BigDecimal.ZERO);
        response.put("setupCostChf", BigDecimal.ZERO);
        response.put("shippingCostChf", BigDecimal.ZERO);
        response.put("globalMachineCostChf", BigDecimal.ZERO);
        response.put("grandTotalChf", BigDecimal.ZERO);
        return response;
    }

    private Map<String, Object> toItemDto(QuoteLineItem item, QuoteSessionTotalsService.QuoteSessionTotals totals) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", item.getId());
        dto.put("lineItemType", item.getLineItemType() != null ? item.getLineItemType() : "PRINT_FILE");
        dto.put("originalFilename", item.getOriginalFilename());
        dto.put(
                "displayName",
                item.getDisplayName() != null && !item.getDisplayName().isBlank()
                        ? item.getDisplayName()
                        : item.getOriginalFilename()
        );
        dto.put("quantity", item.getQuantity());
        dto.put("printTimeSeconds", item.getPrintTimeSeconds());
        dto.put("materialGrams", item.getMaterialGrams());
        dto.put("colorCode", item.getColorCode());
        dto.put("filamentVariantId", item.getFilamentVariant() != null ? item.getFilamentVariant().getId() : null);
        dto.put("shopProductId", item.getShopProduct() != null ? item.getShopProduct().getId() : null);
        dto.put("shopProductVariantId", item.getShopProductVariant() != null ? item.getShopProductVariant().getId() : null);
        dto.put("shopProductSlug", item.getShopProductSlug());
        dto.put("shopProductName", item.getShopProductName());
        dto.put("shopVariantLabel", item.getShopVariantLabel());
        dto.put("shopVariantColorName", item.getShopVariantColorName());
        dto.put("shopVariantColorLabelIt", item.getShopProductVariant() != null ? item.getShopProductVariant().getColorLabelIt() : null);
        dto.put("shopVariantColorLabelEn", item.getShopProductVariant() != null ? item.getShopProductVariant().getColorLabelEn() : null);
        dto.put("shopVariantColorLabelDe", item.getShopProductVariant() != null ? item.getShopProductVariant().getColorLabelDe() : null);
        dto.put("shopVariantColorLabelFr", item.getShopProductVariant() != null ? item.getShopProductVariant().getColorLabelFr() : null);
        dto.put("shopVariantColorHex", item.getShopVariantColorHex());
        dto.put("filamentColorLabelIt", item.getFilamentVariant() != null ? item.getFilamentVariant().getColorLabelIt() : null);
        dto.put("filamentColorLabelEn", item.getFilamentVariant() != null ? item.getFilamentVariant().getColorLabelEn() : null);
        dto.put("filamentColorLabelDe", item.getFilamentVariant() != null ? item.getFilamentVariant().getColorLabelDe() : null);
        dto.put("filamentColorLabelFr", item.getFilamentVariant() != null ? item.getFilamentVariant().getColorLabelFr() : null);
        dto.put("materialCode", item.getMaterialCode());
        dto.put("quality", item.getQuality());
        dto.put("nozzleDiameterMm", item.getNozzleDiameterMm());
        dto.put("layerHeightMm", item.getLayerHeightMm());
        dto.put("infillPercent", item.getInfillPercent());
        dto.put("infillPattern", item.getInfillPattern());
        dto.put("supportsEnabled", item.getSupportsEnabled());
        dto.put("requiresSplitPrinting", Boolean.TRUE.equals(item.getRequiresSplitPrinting()));
        dto.put("status", item.getStatus());
        dto.put("errorMessage", item.getErrorMessage());
        dto.put("errorCode", item.getPricingBreakdown() != null
                ? item.getPricingBreakdown().get("errorCode")
                : null);
        dto.put("shippingOrientations", item.getPricingBreakdown() != null
                ? item.getPricingBreakdown().get("shippingOrientations") : null);
        dto.put("convertedStoredPath", quoteStorageService.extractConvertedStoredPath(item));
        dto.put("unitPriceChf", resolveDistributedUnitPrice(item, totals));
        return dto;
    }

    private BigDecimal resolveDistributedUnitPrice(QuoteLineItem item, QuoteSessionTotalsService.QuoteSessionTotals totals) {
        BigDecimal unitPrice = item.getUnitPriceChf() != null ? item.getUnitPriceChf() : BigDecimal.ZERO;
        int quantity = item.getQuantity() != null && item.getQuantity() > 0 ? item.getQuantity() : 1;
        if (totals.totalPrintSeconds().compareTo(BigDecimal.ZERO) > 0 && item.getPrintTimeSeconds() != null) {
            BigDecimal itemSeconds = BigDecimal.valueOf(item.getPrintTimeSeconds()).multiply(BigDecimal.valueOf(quantity));
            BigDecimal share = itemSeconds.divide(totals.totalPrintSeconds(), 8, RoundingMode.HALF_UP);
            BigDecimal itemMachineCost = totals.globalMachineCostChf().multiply(share);
            BigDecimal unitMachineCost = itemMachineCost.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
            unitPrice = unitPrice.add(unitMachineCost);
        }
        return unitPrice;
    }
}
