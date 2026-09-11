package com.printcalculator.service.quote;

import com.printcalculator.dto.CadCheckoutItemRequest;
import com.printcalculator.entity.*;
import com.printcalculator.repository.*;
import com.printcalculator.service.QuoteSessionTotalsService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CadCheckoutServiceTest {
    final QuoteLineItemRepository items = mock(QuoteLineItemRepository.class);
    final FilamentVariantRepository variants = mock(FilamentVariantRepository.class);
    final PricingPolicyRepository policies = mock(PricingPolicyRepository.class);
    final QuoteSessionTotalsService totals = mock(QuoteSessionTotalsService.class);
    final QuoteSessionResponseAssembler assembler = mock(QuoteSessionResponseAssembler.class);
    final CadCheckoutService service = new CadCheckoutService(items, variants, policies, totals, assembler);
    final QuoteSession session = new QuoteSession();
    final QuoteLineItem item = new QuoteLineItem();
    final FilamentVariant black = variant(1L, "PLA", "Black", "20");
    final FilamentVariant red = variant(2L, "PLA", "Red", "30");

    CadCheckoutServiceTest() {
        session.setId(UUID.randomUUID()); session.setStatus("CAD_ACTIVE");
        item.setId(UUID.randomUUID()); item.setQuoteSession(session); item.setStatus("READY");
        item.setMaterialCode("PLA"); item.setFilamentVariant(black); item.setQuantity(1);
        item.setMaterialGrams(new BigDecimal("100")); item.setUnitPriceChf(new BigDecimal("5.00"));
        when(items.findById(item.getId())).thenReturn(Optional.of(item));
        when(items.findByQuoteSessionIdOrderByCreatedAtAsc(session.getId())).thenReturn(List.of(item));
        when(variants.findById(1L)).thenReturn(Optional.of(black));
        when(variants.findById(2L)).thenReturn(Optional.of(red));
        PricingPolicy policy = new PricingPolicy(); policy.setMarkupPercent(new BigDecimal("50"));
        when(policies.findFirstByIsActiveTrueOrderByValidFromDesc()).thenReturn(policy);
    }

    @Test void recalculatesColorAndQuantityAndReturnsServerTotals() {
        service.update(session.getId(), item.getId(), new CadCheckoutItemRequest(3, 2L));
        assertEquals(3, item.getQuantity());
        assertEquals(new BigDecimal("6.50"), item.getUnitPriceChf());
        assertEquals("Red", item.getColorCode());
        verify(totals).compute(session, List.of(item));
        verify(assembler).assemble(eq(session), eq(List.of(item)), isNull());
        for (int i = 0; i < 5; i++) {
            service.update(session.getId(), item.getId(), new CadCheckoutItemRequest(3, 1L));
            assertEquals(new BigDecimal("5.00"), item.getUnitPriceChf());
            service.update(session.getId(), item.getId(), new CadCheckoutItemRequest(3, 2L));
            assertEquals(new BigDecimal("6.50"), item.getUnitPriceChf());
        }
    }

    @Test void rejectsOtherSessionAndConvertedOrders() {
        assertThrows(ResponseStatusException.class, () -> service.update(UUID.randomUUID(), item.getId(), new CadCheckoutItemRequest(2, 2L)));
        session.setStatus("CONVERTED");
        assertThrows(ResponseStatusException.class, () -> service.update(session.getId(), item.getId(), new CadCheckoutItemRequest(2, 2L)));
        verify(items, never()).save(any());
    }

    @Test void rejectsInactiveAndDifferentMaterialsBeforeMutation() {
        red.setIsActive(false);
        assertThrows(ResponseStatusException.class, () -> service.update(session.getId(), item.getId(), new CadCheckoutItemRequest(2, 2L)));
        red.setIsActive(true); red.getFilamentMaterialType().setMaterialCode("PETG");
        assertThrows(ResponseStatusException.class, () -> service.update(session.getId(), item.getId(), new CadCheckoutItemRequest(2, 2L)));
        assertEquals(1, item.getQuantity());
        verify(items, never()).save(any());
    }

    private static FilamentVariant variant(long id, String material, String color, String cost) {
        FilamentMaterialType type = new FilamentMaterialType(); type.setMaterialCode(material);
        FilamentVariant variant = new FilamentVariant(); variant.setId(id); variant.setIsActive(true);
        variant.setFilamentMaterialType(type); variant.setColorName(color); variant.setCostChfPerKg(new BigDecimal(cost));
        return variant;
    }
}
