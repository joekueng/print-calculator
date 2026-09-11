package com.printcalculator.service.quote;

import com.printcalculator.dto.CadCheckoutItemRequest;
import com.printcalculator.repository.*;
import com.printcalculator.service.QuoteSessionTotalsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import static org.springframework.http.HttpStatus.*;

@Service
public class CadCheckoutService {
    private final QuoteLineItemRepository items;
    private final FilamentVariantRepository variants;
    private final PricingPolicyRepository policies;
    private final QuoteSessionTotalsService totals;
    private final QuoteSessionResponseAssembler assembler;

    public CadCheckoutService(QuoteLineItemRepository items, FilamentVariantRepository variants,
                              PricingPolicyRepository policies, QuoteSessionTotalsService totals,
                              QuoteSessionResponseAssembler assembler) {
        this.items = items;
        this.variants = variants;
        this.policies = policies;
        this.totals = totals;
        this.assembler = assembler;
    }

    @Transactional
    public Map<String, Object> update(UUID sessionId, UUID itemId, CadCheckoutItemRequest request) {
        var item = items.findById(itemId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        var session = item.getQuoteSession();
        if (!sessionId.equals(session.getId())) throw new ResponseStatusException(NOT_FOUND);
        if (!"CAD_ACTIVE".equals(session.getStatus()) || !"READY".equals(item.getStatus())
                || "SHOP_PRODUCT".equals(item.getLineItemType())) {
            throw new ResponseStatusException(BAD_REQUEST, "This item cannot be edited at CAD checkout");
        }
        var variant = variants.findById(request.filamentVariantId())
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unknown color"));
        if (!Boolean.TRUE.equals(variant.getIsActive())
                || !item.getMaterialCode().equals(variant.getFilamentMaterialType().getMaterialCode())) {
            throw new ResponseStatusException(BAD_REQUEST, "Choose an active color of the same material");
        }
        var previous = item.getFilamentVariant();
        if (previous == null) throw new ResponseStatusException(BAD_REQUEST, "Missing original material variant");
        if (!previous.getId().equals(variant.getId())) {
            var policy = policies.findFirstByIsActiveTrueOrderByValidFromDesc();
            if (policy == null || item.getMaterialGrams() == null) {
                throw new ResponseStatusException(BAD_REQUEST, "Pricing is unavailable");
            }
            // Keep a stable pricing basis so switching colors repeatedly cannot accumulate rounding errors.
            var breakdown = new java.util.HashMap<String, Object>(item.getPricingBreakdown() != null
                    ? item.getPricingBreakdown() : Map.of());
            breakdown.putIfAbsent("cadColorBaseUnitPrice", item.getUnitPriceChf());
            breakdown.putIfAbsent("cadColorBaseCostPerKg", previous.getCostChfPerKg());
            breakdown.putIfAbsent("cadColorMarkup", BigDecimal.ONE.add(policy.getMarkupPercent().movePointLeft(2)));
            BigDecimal basePrice = new BigDecimal(breakdown.get("cadColorBaseUnitPrice").toString());
            BigDecimal baseCost = new BigDecimal(breakdown.get("cadColorBaseCostPerKg").toString());
            BigDecimal markup = new BigDecimal(breakdown.get("cadColorMarkup").toString());
            BigDecimal difference = item.getMaterialGrams().divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP)
                    .multiply(variant.getCostChfPerKg().subtract(baseCost)).multiply(markup);
            item.setUnitPriceChf(basePrice.add(difference).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
            breakdown.put("machine_cost", item.getUnitPriceChf());
            item.setPricingBreakdown(breakdown);
        }
        item.setQuantity(request.quantity());
        item.setFilamentVariant(variant);
        item.setColorCode(variant.getColorName());
        item.setUpdatedAt(OffsetDateTime.now());
        items.save(item);
        var allItems = items.findByQuoteSessionIdOrderByCreatedAtAsc(sessionId);
        return assembler.assemble(session, allItems, totals.compute(session, allItems));
    }
}
