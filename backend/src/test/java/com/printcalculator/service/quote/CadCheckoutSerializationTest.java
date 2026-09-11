package com.printcalculator.service.quote;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.printcalculator.controller.CadCheckoutController;
import com.printcalculator.dto.CadCheckoutItemRequest;
import com.printcalculator.entity.*;
import com.printcalculator.repository.*;
import com.printcalculator.service.QuoteSessionTotalsService;
import org.hibernate.cfg.Configuration;
import org.hibernate.proxy.HibernateProxy;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CadCheckoutSerializationTest {
    @Test
    void cadUpdateSerializesAfterTransactionClosesWithARealHibernateSessionProxy() throws Exception {
        try (var factory = new Configuration().addAnnotatedClass(QuoteSession.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:cadSerialization;DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .buildSessionFactory()) {
            var entityManager = factory.openSession();
            var transaction = entityManager.beginTransaction();
            var session = new QuoteSession();
            session.setStatus("CAD_ACTIVE"); session.setSessionType("PRINT_QUOTE");
            session.setPricingVersion("v1"); session.setMaterialCode("PLA");
            session.setSetupCostChf(BigDecimal.ZERO); session.setExpiresAt(OffsetDateTime.now().plusDays(30));
            session.setCadHours(new BigDecimal("2.50"));
            entityManager.persist(session); entityManager.flush(); entityManager.clear();
            var proxy = entityManager.getReference(QuoteSession.class, session.getId());
            assertInstanceOf(HibernateProxy.class, proxy);

            var material = new FilamentMaterialType(); material.setMaterialCode("PLA");
            var variant = new FilamentVariant(); variant.setId(16L); variant.setIsActive(true);
            variant.setFilamentMaterialType(material); variant.setColorName("Arancione");
            var item = new QuoteLineItem(); item.setId(UUID.randomUUID()); item.setQuoteSession(proxy);
            item.setStatus("READY"); item.setMaterialCode("PLA"); item.setFilamentVariant(variant);
            item.setQuantity(1); item.setUnitPriceChf(new BigDecimal("4.00"));
            var items = mock(QuoteLineItemRepository.class);
            var variants = mock(FilamentVariantRepository.class);
            var totals = mock(QuoteSessionTotalsService.class);
            when(items.findById(item.getId())).thenReturn(java.util.Optional.of(item));
            when(items.findByQuoteSessionIdOrderByCreatedAtAsc(session.getId())).thenReturn(List.of(item));
            when(variants.findById(16L)).thenReturn(java.util.Optional.of(variant));
            var zero = BigDecimal.ZERO;
            when(totals.compute(proxy, List.of(item))).thenReturn(new QuoteSessionTotalsService.QuoteSessionTotals(
                    zero, zero, zero, zero, zero, zero, zero, zero, new BigDecimal("12.00"), zero));
            var service = new CadCheckoutService(items, variants, mock(PricingPolicyRepository.class), totals,
                    new QuoteSessionResponseAssembler(mock(QuoteStorageService.class)));
            var response = service.update(session.getId(), item.getId(), new CadCheckoutItemRequest(3, 16L));
            // The original entity-based response cannot be serialized, even while its proxy is initialized.
            var mapper = JsonMapper.builder().findAndAddModules().build();
            assertThrows(com.fasterxml.jackson.databind.exc.InvalidDefinitionException.class,
                    () -> mapper.writeValueAsString(proxy));
            transaction.commit(); entityManager.close();

            var endpointService = mock(CadCheckoutService.class);
            when(endpointService.update(any(), any(), any())).thenReturn(response);
            var mvc = MockMvcBuilders.standaloneSetup(new CadCheckoutController(endpointService)).build();
            mvc.perform(patch("/api/quote-sessions/{sessionId}/cad-items/{itemId}", session.getId(), item.getId())
                            .contentType("application/json").content("{\"quantity\":3,\"filamentVariantId\":16}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.session.id").value(session.getId().toString()))
                    .andExpect(jsonPath("$.session.status").value("CAD_ACTIVE"))
                    .andExpect(jsonPath("$.session.cadHours").value(2.5))
                    .andExpect(jsonPath("$.session.hibernateLazyInitializer").doesNotExist())
                    .andExpect(jsonPath("$.items[0].quantity").value(3))
                    .andExpect(jsonPath("$.items[0].filamentVariantId").value(16))
                    .andExpect(jsonPath("$.grandTotalChf").value(12));
        }
    }
}
