package com.printcalculator.service;

import com.printcalculator.entity.PricingPolicy;
import com.printcalculator.entity.QuoteLineItem;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.entity.NozzleOption;
import com.printcalculator.repository.NozzleOptionRepository;
import com.printcalculator.repository.PricingPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuoteSessionTotalsServiceTest {
    private PricingPolicyRepository pricingRepo;
    private QuoteCalculator quoteCalculator;
    private NozzleOptionRepository nozzleOptionRepo;
    private QuoteSessionTotalsService service;

    @BeforeEach
    void setUp() {
        pricingRepo = mock(PricingPolicyRepository.class);
        quoteCalculator = mock(QuoteCalculator.class);
        nozzleOptionRepo = mock(NozzleOptionRepository.class);
        service = new QuoteSessionTotalsService(pricingRepo, quoteCalculator, nozzleOptionRepo,
                new ShippingQuoteService(3, ShippingQuoteService.DEFAULT_PROFILES));
    }

    @Test
    void compute_WithCadOnlySession_ShouldIncludeCadAndNoShipping() {
        QuoteSession session = new QuoteSession();
        session.setSetupCostChf(BigDecimal.ZERO);
        session.setCadHours(BigDecimal.valueOf(2));
        session.setCadHourlyRateChf(BigDecimal.valueOf(75));

        PricingPolicy policy = new PricingPolicy();
        when(pricingRepo.findFirstByIsActiveTrueOrderByValidFromDesc()).thenReturn(policy);
        when(quoteCalculator.calculateSessionMachineCost(eq(policy), any(BigDecimal.class))).thenReturn(BigDecimal.ZERO);

        QuoteSessionTotalsService.QuoteSessionTotals totals = service.compute(session, List.of());

        assertAmountEquals("150.00", totals.cadTotalChf());
        assertAmountEquals("0.00", totals.shippingCostChf());
        assertAmountEquals("150.00", totals.itemsTotalChf());
        assertAmountEquals("150.00", totals.grandTotalChf());
    }

    @Test
    void compute_ShopUsesOnlyFourNineAndTwelveFrancShippingTiers() {
        QuoteSession session = new QuoteSession();
        session.setSessionType("SHOP_CART");
        session.setSetupCostChf(BigDecimal.ZERO);
        when(quoteCalculator.calculateSessionMachineCost(any(), any())).thenReturn(BigDecimal.ZERO);

        QuoteLineItem compact = createItem(BigDecimal.TEN, 1, 0, "0.4");
        compact.setLineItemType("SHOP_PRODUCT");
        compact.setMaterialGrams(null);
        var compactTotals = service.compute(session, List.of(compact));
        assertAmountEquals("4.00", compactTotals.shippingCostChf());
        org.junit.jupiter.api.Assertions.assertNull(compactTotals.shippingQuote());

        QuoteLineItem standard = createItem(BigDecimal.TEN, 5, 0, "0.4");
        standard.setLineItemType("SHOP_PRODUCT");
        standard.setMaterialGrams(null);
        standard.setBoundingBoxXMm(BigDecimal.valueOf(300));
        assertAmountEquals("9.00", service.compute(session, List.of(standard)).shippingCostChf());

        QuoteLineItem largeOrder = createItem(BigDecimal.TEN, 6, 0, "0.4");
        largeOrder.setLineItemType("SHOP_PRODUCT");
        largeOrder.setMaterialGrams(null);
        largeOrder.setBoundingBoxXMm(BigDecimal.valueOf(300));
        assertAmountEquals("12.00", service.compute(session, List.of(largeOrder)).shippingCostChf());
    }

    @Test
    void compute_WithPrintItemAndCad_ShouldSumEverything() {
        QuoteSession session = new QuoteSession();
        session.setSetupCostChf(new BigDecimal("5.00"));
        session.setCadHours(new BigDecimal("1.50"));
        session.setCadHourlyRateChf(new BigDecimal("60.00"));

        QuoteLineItem item = new QuoteLineItem();
        item.setQuantity(2);
        item.setMaterialGrams(BigDecimal.TEN);
        item.setUnitPriceChf(new BigDecimal("10.00"));
        item.setPrintTimeSeconds(3600);
        item.setBoundingBoxXMm(new BigDecimal("10"));
        item.setBoundingBoxYMm(new BigDecimal("10"));
        item.setBoundingBoxZMm(new BigDecimal("10"));

        PricingPolicy policy = new PricingPolicy();
        when(pricingRepo.findFirstByIsActiveTrueOrderByValidFromDesc()).thenReturn(policy);
        when(quoteCalculator.calculateSessionMachineCost(policy, new BigDecimal("2.0000")))
                .thenReturn(new BigDecimal("3.00"));

        QuoteSessionTotalsService.QuoteSessionTotals totals = service.compute(session, List.of(item));

        assertAmountEquals("23.00", totals.printItemsTotalChf());
        assertAmountEquals("90.00", totals.cadTotalChf());
        assertAmountEquals("113.00", totals.itemsTotalChf());
        assertAmountEquals("2.00", totals.shippingCostChf());
        assertAmountEquals("120.00", totals.grandTotalChf());
    }

    @Test
    void compute_WithRepeatedNozzleAcrossItems_ShouldChargeNozzleFeeOnlyOncePerType() {
        QuoteSession session = new QuoteSession();
        session.setSetupCostChf(new BigDecimal("2.00"));

        QuoteLineItem itemA = createItem(new BigDecimal("10.00"), 3, 3600, "0.60");
        QuoteLineItem itemB = createItem(new BigDecimal("4.00"), 2, 1200, "0.60");
        QuoteLineItem itemC = createItem(new BigDecimal("5.00"), 1, 600, "0.80");

        PricingPolicy policy = new PricingPolicy();
        when(pricingRepo.findFirstByIsActiveTrueOrderByValidFromDesc()).thenReturn(policy);
        when(quoteCalculator.calculateSessionMachineCost(eq(policy), any(BigDecimal.class))).thenReturn(BigDecimal.ZERO);
        when(nozzleOptionRepo.findFirstByNozzleDiameterMmAndIsActiveTrue(new BigDecimal("0.60")))
                .thenReturn(java.util.Optional.of(nozzleOption("0.60", "1.50")));
        when(nozzleOptionRepo.findFirstByNozzleDiameterMmAndIsActiveTrue(new BigDecimal("0.80")))
                .thenReturn(java.util.Optional.of(nozzleOption("0.80", "1.50")));

        QuoteSessionTotalsService.QuoteSessionTotals totals = service.compute(session, List.of(itemA, itemB, itemC));

        assertAmountEquals("43.00", totals.itemsTotalChf());
        assertAmountEquals("3.00", totals.nozzleChangeCostChf());
        assertAmountEquals("5.00", totals.setupCostChf());
        assertAmountEquals("50.00", totals.grandTotalChf());
    }

    @Test
    void compute_WithSplitPrintingItem_ShouldUseSplitSetupFee() {
        QuoteSession session = new QuoteSession();
        session.setSetupCostChf(new BigDecimal("2.00"));

        QuoteLineItem item = createItem(new BigDecimal("12.00"), 1, 1800, "0.40");
        item.setRequiresSplitPrinting(true);

        PricingPolicy policy = new PricingPolicy();
        policy.setSplitModelSetupFeeChf(new BigDecimal("10.00"));
        when(pricingRepo.findFirstByIsActiveTrueOrderByValidFromDesc()).thenReturn(policy);
        when(quoteCalculator.calculateSessionMachineCost(eq(policy), any(BigDecimal.class))).thenReturn(BigDecimal.ZERO);
        when(quoteCalculator.calculateSplitModelSetupFee(policy)).thenReturn(new BigDecimal("10.00"));
        when(nozzleOptionRepo.findFirstByNozzleDiameterMmAndIsActiveTrue(new BigDecimal("0.40")))
                .thenReturn(java.util.Optional.empty());

        QuoteSessionTotalsService.QuoteSessionTotals totals = service.compute(session, List.of(item));

        assertAmountEquals("12.00", totals.baseSetupCostChf());
        assertAmountEquals("12.00", totals.setupCostChf());
        assertAmountEquals("26.00", totals.grandTotalChf());
    }

    @Test
    void compute_WithReviewRequiredItem_ShouldExcludeItFromTotalsAndShipping() {
        QuoteSession session = new QuoteSession();
        session.setSetupCostChf(new BigDecimal("2.00"));

        QuoteLineItem ready = createItem(new BigDecimal("8.00"), 1, 1800, "0.40");
        ready.setStatus("READY");
        QuoteLineItem reviewRequired = createItem(new BigDecimal("99.00"), 4, 7200, "0.80");
        reviewRequired.setStatus("REVIEW_REQUIRED");

        PricingPolicy policy = new PricingPolicy();
        when(pricingRepo.findFirstByIsActiveTrueOrderByValidFromDesc()).thenReturn(policy);
        when(quoteCalculator.calculateSessionMachineCost(eq(policy), any(BigDecimal.class))).thenReturn(BigDecimal.ZERO);
        when(nozzleOptionRepo.findFirstByNozzleDiameterMmAndIsActiveTrue(new BigDecimal("0.40")))
                .thenReturn(java.util.Optional.empty());

        QuoteSessionTotalsService.QuoteSessionTotals totals = service.compute(
                session,
                List.of(ready, reviewRequired)
        );

        assertAmountEquals("8.00", totals.itemsTotalChf());
        assertAmountEquals("2.00", totals.shippingCostChf());
        assertAmountEquals("12.00", totals.grandTotalChf());
    }

    private QuoteLineItem createItem(BigDecimal unitPrice, int quantity, int printSeconds, String nozzleMm) {
        QuoteLineItem item = new QuoteLineItem();
        item.setQuantity(quantity);
        item.setMaterialGrams(BigDecimal.TEN);
        item.setUnitPriceChf(unitPrice);
        item.setPrintTimeSeconds(printSeconds);
        item.setNozzleDiameterMm(new BigDecimal(nozzleMm));
        item.setBoundingBoxXMm(new BigDecimal("10"));
        item.setBoundingBoxYMm(new BigDecimal("10"));
        item.setBoundingBoxZMm(new BigDecimal("10"));
        return item;
    }

    private NozzleOption nozzleOption(String diameterMm, String feeChf) {
        NozzleOption option = new NozzleOption();
        option.setNozzleDiameterMm(new BigDecimal(diameterMm));
        option.setExtraNozzleChangeFeeChf(new BigDecimal(feeChf));
        option.setIsActive(true);
        return option;
    }

    private void assertAmountEquals(String expected, BigDecimal actual) {
        assertTrue(new BigDecimal(expected).compareTo(actual) == 0,
                "Expected " + expected + " but got " + actual);
    }
}
