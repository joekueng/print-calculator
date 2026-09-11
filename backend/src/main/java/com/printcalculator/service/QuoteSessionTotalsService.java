package com.printcalculator.service;

import com.printcalculator.entity.PricingPolicy;
import com.printcalculator.entity.QuoteLineItem;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.repository.NozzleOptionRepository;
import com.printcalculator.repository.PricingPolicyRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Service
public class QuoteSessionTotalsService {
    private final PricingPolicyRepository pricingRepo;
    private final QuoteCalculator quoteCalculator;
    private final NozzleOptionRepository nozzleOptionRepo;
    private final ShippingQuoteService shippingQuoteService;

    public QuoteSessionTotalsService(PricingPolicyRepository pricingRepo,
                                     QuoteCalculator quoteCalculator,
                                     NozzleOptionRepository nozzleOptionRepo,
                                     ShippingQuoteService shippingQuoteService) {
        this.pricingRepo = pricingRepo;
        this.quoteCalculator = quoteCalculator;
        this.nozzleOptionRepo = nozzleOptionRepo;
        this.shippingQuoteService = shippingQuoteService;
    }

    public QuoteSessionTotals compute(QuoteSession session, List<QuoteLineItem> items) {
        BigDecimal printItemsBaseTotal = BigDecimal.ZERO;
        BigDecimal totalSeconds = BigDecimal.ZERO;

        for (QuoteLineItem item : items) {
            if (!isOrderable(item)) {
                continue;
            }
            int quantity = normalizeQuantity(item.getQuantity());
            BigDecimal unitPrice = item.getUnitPriceChf() != null ? item.getUnitPriceChf() : BigDecimal.ZERO;
            printItemsBaseTotal = printItemsBaseTotal.add(unitPrice.multiply(BigDecimal.valueOf(quantity)));

            if (item.getPrintTimeSeconds() != null && item.getPrintTimeSeconds() > 0) {
                totalSeconds = totalSeconds.add(BigDecimal.valueOf(item.getPrintTimeSeconds()).multiply(BigDecimal.valueOf(quantity)));
            }
        }

        BigDecimal totalHours = totalSeconds.divide(BigDecimal.valueOf(3600), 4, RoundingMode.HALF_UP);
        PricingPolicy policy = pricingRepo.findFirstByIsActiveTrueOrderByValidFromDesc();
        BigDecimal globalMachineCost = quoteCalculator.calculateSessionMachineCost(policy, totalHours);
        BigDecimal printItemsTotal = printItemsBaseTotal.add(globalMachineCost);

        BigDecimal cadTotal = calculateCadTotal(session);
        BigDecimal itemsTotal = printItemsTotal.add(cadTotal);

        BigDecimal standardSetupFee = session.getSetupCostChf() != null
                ? session.getSetupCostChf()
                : BigDecimal.ZERO;
        BigDecimal splitSetupFee = hasSplitPrintingItems(items)
                ? quoteCalculator.calculateSplitModelSetupFee(policy)
                : BigDecimal.ZERO;
        BigDecimal baseSetupFee = standardSetupFee.add(splitSetupFee);
        BigDecimal nozzleChangeCost = calculateNozzleChangeCost(items);
        BigDecimal setupFee = baseSetupFee.add(nozzleChangeCost).setScale(2, RoundingMode.HALF_UP);
        boolean shop = "SHOP_CART".equalsIgnoreCase(session.getSessionType())
                || (!items.isEmpty() && items.stream().allMatch(i -> "SHOP_PRODUCT".equalsIgnoreCase(i.getLineItemType())));
        List<QuoteLineItem> orderableItems = items.stream()
                .filter(this::isOrderable)
                .toList();
        ShippingQuoteService.ShippingQuote shippingQuote = shop ? null : shippingQuoteService.quote(orderableItems);
        BigDecimal shippingCost = shop ? calculateShopShippingCost(items) : shippingQuote.costChf();
        BigDecimal grandTotal = itemsTotal.add(setupFee).add(shippingCost);

        return new QuoteSessionTotals(
                printItemsTotal,
                globalMachineCost,
                cadTotal,
                itemsTotal,
                baseSetupFee.setScale(2, RoundingMode.HALF_UP),
                nozzleChangeCost,
                setupFee,
                shippingCost,
                grandTotal,
                totalSeconds,
                shippingQuote
        );
    }

    public BigDecimal calculateCadTotal(QuoteSession session) {
        if (session == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal cadHours = session.getCadHours() != null ? session.getCadHours() : BigDecimal.ZERO;
        BigDecimal cadRate = session.getCadHourlyRateChf() != null ? session.getCadHourlyRateChf() : BigDecimal.ZERO;
        if (cadHours.compareTo(BigDecimal.ZERO) <= 0 || cadRate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return cadHours.multiply(cadRate).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateShopShippingCost(List<QuoteLineItem> items) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }

        boolean exceedsBaseSize = false;
        for (QuoteLineItem item : items) {
            if (!isOrderable(item)) {
                continue;
            }
            BigDecimal x = item.getBoundingBoxXMm() != null ? item.getBoundingBoxXMm() : BigDecimal.ZERO;
            BigDecimal y = item.getBoundingBoxYMm() != null ? item.getBoundingBoxYMm() : BigDecimal.ZERO;
            BigDecimal z = item.getBoundingBoxZMm() != null ? item.getBoundingBoxZMm() : BigDecimal.ZERO;

            BigDecimal[] dims = {x, y, z};
            Arrays.sort(dims);

            if (dims[2].compareTo(BigDecimal.valueOf(250.0)) > 0
                    || dims[1].compareTo(BigDecimal.valueOf(176.0)) > 0
                    || dims[0].compareTo(BigDecimal.valueOf(20.0)) > 0) {
                exceedsBaseSize = true;
                break;
            }
        }

        int totalQuantity = items.stream()
                .filter(this::isOrderable)
                .mapToInt(i -> normalizeQuantity(i.getQuantity()))
                .sum();
        if (totalQuantity <= 0) {
            return BigDecimal.ZERO;
        }

        if (exceedsBaseSize) {
            return totalQuantity > 5 ? BigDecimal.valueOf(12.00) : BigDecimal.valueOf(9.00);
        }
        return BigDecimal.valueOf(4.00);
    }

    private BigDecimal calculateNozzleChangeCost(List<QuoteLineItem> items) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        Set<BigDecimal> uniqueNozzles = new LinkedHashSet<>();
        for (QuoteLineItem item : items) {
            if (!isOrderable(item) || item.getNozzleDiameterMm() == null) {
                continue;
            }
            uniqueNozzles.add(item.getNozzleDiameterMm().setScale(2, RoundingMode.HALF_UP));
        }

        BigDecimal totalFee = BigDecimal.ZERO;
        for (BigDecimal nozzle : uniqueNozzles) {
            BigDecimal nozzleFee = nozzleOptionRepo
                    .findFirstByNozzleDiameterMmAndIsActiveTrue(nozzle)
                    .map(option -> option.getExtraNozzleChangeFeeChf() != null
                            ? option.getExtraNozzleChangeFeeChf()
                            : BigDecimal.ZERO)
                    .orElse(BigDecimal.ZERO);

            if (nozzleFee.compareTo(BigDecimal.ZERO) > 0) {
                totalFee = totalFee.add(nozzleFee);
            }
        }

        return totalFee.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean hasSplitPrintingItems(List<QuoteLineItem> items) {
        if (items == null || items.isEmpty()) {
            return false;
        }
        return items.stream().anyMatch(item -> isOrderable(item) && Boolean.TRUE.equals(item.getRequiresSplitPrinting()));
    }

    private boolean isOrderable(QuoteLineItem item) {
        return item != null && !"REVIEW_REQUIRED".equalsIgnoreCase(item.getStatus());
    }

    private int normalizeQuantity(Integer quantity) {
        if (quantity == null || quantity < 1) {
            return 1;
        }
        return quantity;
    }

    public record QuoteSessionTotals(
            BigDecimal printItemsTotalChf,
            BigDecimal globalMachineCostChf,
            BigDecimal cadTotalChf,
            BigDecimal itemsTotalChf,
            BigDecimal baseSetupCostChf,
            BigDecimal nozzleChangeCostChf,
            BigDecimal setupCostChf,
            BigDecimal shippingCostChf,
            BigDecimal grandTotalChf,
            BigDecimal totalPrintSeconds,
            ShippingQuoteService.ShippingQuote shippingQuote
    ) {
        public QuoteSessionTotals(BigDecimal printItemsTotalChf, BigDecimal globalMachineCostChf,
                BigDecimal cadTotalChf, BigDecimal itemsTotalChf, BigDecimal baseSetupCostChf,
                BigDecimal nozzleChangeCostChf, BigDecimal setupCostChf, BigDecimal shippingCostChf,
                BigDecimal grandTotalChf, BigDecimal totalPrintSeconds) {
            this(printItemsTotalChf, globalMachineCostChf, cadTotalChf, itemsTotalChf, baseSetupCostChf,
                    nozzleChangeCostChf, setupCostChf, shippingCostChf, grandTotalChf, totalPrintSeconds, null);
        }
    }
}
