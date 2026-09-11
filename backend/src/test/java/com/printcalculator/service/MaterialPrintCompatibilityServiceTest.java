package com.printcalculator.service;

import com.printcalculator.entity.FilamentMaterialType;
import com.printcalculator.entity.FilamentVariant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MaterialPrintCompatibilityServiceTest {
    private final MaterialPrintCompatibilityService service = new MaterialPrintCompatibilityService();

    @Test
    void rejectsBlockedNozzlesForTechnicalMaterials() {
        FilamentVariant variant = variant(true);

        ResponseStatusException smallNozzle = assertThrows(
                ResponseStatusException.class,
                () -> service.validate(variant, new BigDecimal("0.2"), new BigDecimal("0.12"))
        );
        ResponseStatusException largeNozzle = assertThrows(
                ResponseStatusException.class,
                () -> service.validate(variant, new BigDecimal("0.80"), new BigDecimal("0.20"))
        );

        assertEquals(HttpStatus.BAD_REQUEST, smallNozzle.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, largeNozzle.getStatusCode());
    }

    @Test
    void rejectsLayersBelowMinimumForTechnicalMaterials() {
        FilamentVariant variant = variant(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.validate(variant, new BigDecimal("0.4"), new BigDecimal("0.119"))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void acceptsBoundaryAndDoesNotRestrictStandardMaterials() {
        assertDoesNotThrow(() -> service.validate(
                variant(true),
                new BigDecimal("0.4"),
                new BigDecimal("0.120")
        ));
        assertDoesNotThrow(() -> service.validate(
                variant(false),
                new BigDecimal("0.2"),
                new BigDecimal("0.08")
        ));
    }

    private FilamentVariant variant(boolean technical) {
        FilamentMaterialType materialType = new FilamentMaterialType();
        materialType.setIsTechnical(technical);
        FilamentVariant variant = new FilamentVariant();
        variant.setFilamentMaterialType(materialType);
        return variant;
    }
}
