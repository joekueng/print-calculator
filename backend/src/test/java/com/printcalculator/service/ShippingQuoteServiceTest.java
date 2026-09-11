package com.printcalculator.service;

import com.printcalculator.entity.QuoteLineItem;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ShippingQuoteServiceTest {
    private final ShippingQuoteService service = new ShippingQuoteService(3, ShippingQuoteService.DEFAULT_PROFILES);

    private QuoteLineItem item(double x,double y,double z,double grams,int quantity) {
        QuoteLineItem item = new QuoteLineItem();
        item.setId(UUID.randomUUID()); item.setQuantity(quantity); item.setStatus("READY");
        item.setBoundingBoxXMm(BigDecimal.valueOf(x)); item.setBoundingBoxYMm(BigDecimal.valueOf(y));
        item.setBoundingBoxZMm(BigDecimal.valueOf(z)); item.setMaterialGrams(BigDecimal.valueOf(grams));
        return item;
    }
    private void price(int expected, QuoteLineItem item) {
        var quote = service.quote(List.of(item));
        assertEquals("QUOTED",quote.status());
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(quote.costChf()));
    }
    @Test void tiersIncludePaddingAndPackagingWeight() {
        price(2,item(100,60,12,980,1));
        price(9,item(100,60,12,981,1));
        price(4,item(100,60,20,460,1));
        price(9,item(100,60,20,461,1));
        price(9,item(100,60,60,1700,1));
        price(12,item(100,60,60,1701,1));
        price(12,item(100,60,60,9700,1));
        price(25,item(100,60,60,9701,1));
        price(25,item(100,60,60,29500,1));
        assertEquals("MANUAL_QUOTE",service.quote(List.of(item(100,60,60,29501,1))).status());
    }
    @Test void rotatesAndUsesAllCopiesWithoutOverlap() {
        var quote = service.quote(List.of(item(12,100,60,10,6)));
        assertEquals(new BigDecimal("2.00"),quote.costChf());
        assertEquals(6,quote.placements().size());
        for (var a : quote.placements()) {
            assertTrue(a.x() >= 0 && a.y() >= 0 && a.z() >= 0);
            assertTrue(a.x()+a.sizeX() <= quote.packageProfile().innerX());
            assertTrue(a.y()+a.sizeY() <= quote.packageProfile().innerY());
            assertTrue(a.z()+a.sizeZ() <= quote.packageProfile().innerZ());
            for (var b : quote.placements()) if (a != b) assertTrue(
                    a.x()+a.sizeX() <= b.x() || b.x()+b.sizeX() <= a.x()
                    || a.y()+a.sizeY() <= b.y() || b.y()+b.sizeY() <= a.y()
                    || a.z()+a.sizeZ() <= b.z() || b.z()+b.sizeZ() <= a.z());
        }
    }
    @Test void quantityChangesPackageAndWeightBand() {
        price(2,item(200,150,12,50,1));
        price(4,item(200,150,12,50,2));
        price(9,item(200,150,12,50,3));
        price(12,item(100,60,60,900,2));
    }
    @Test void missingDataAndOversizedAreNotFreeShipping() {
        assertEquals("NOT_REQUIRED",service.quote(List.of()).status());
        var missing = item(0,60,12,10,1);
        assertEquals("PENDING",service.quote(List.of(missing)).status());
        missing = item(10,60,12,10,1); missing.setMaterialGrams(null);
        assertFalse(service.quote(List.of(missing)).available());
        assertEquals("MANUAL_QUOTE",service.quote(List.of(item(1001,20,20,10,1))).status());
        assertEquals("MANUAL_QUOTE",service.quote(List.of(item(10,10,10,10,501))).status());
    }
    @Test void validatesConfigurationAndHonoursChangedPadding() {
        assertThrows(IllegalArgumentException.class,() -> new ShippingQuoteService(-1,ShippingQuoteService.DEFAULT_PROFILES));
        assertThrows(IllegalArgumentException.class,() -> new ShippingQuoteService(3,"BAD,400,400,400,500,500,500,20,1000,2"));
        assertEquals(new BigDecimal("4.00"), new ShippingQuoteService(4,ShippingQuoteService.DEFAULT_PROFILES)
                .quote(List.of(item(100,60,12,10,1))).costChf());
    }
}
