package com.printcalculator.service;

import com.printcalculator.entity.FilamentMaterialType;
import com.printcalculator.entity.FilamentVariant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Set;

@Service
public class MaterialPrintCompatibilityService {
    private static final Set<BigDecimal> TECHNICAL_MATERIAL_BLOCKED_NOZZLES = Set.of(
            new BigDecimal("0.20"),
            new BigDecimal("0.80")
    );
    private static final BigDecimal TECHNICAL_MATERIAL_MIN_LAYER_HEIGHT = new BigDecimal("0.120");

    public void validate(FilamentVariant variant, BigDecimal nozzleDiameter, BigDecimal layerHeight) {
        FilamentMaterialType materialType = variant != null ? variant.getFilamentMaterialType() : null;
        if (materialType == null || !Boolean.TRUE.equals(materialType.getIsTechnical())) {
            return;
        }

        if (nozzleDiameter != null && TECHNICAL_MATERIAL_BLOCKED_NOZZLES.stream()
                .anyMatch(blocked -> blocked.compareTo(nozzleDiameter) == 0)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Technical materials cannot be printed with 0.2 mm or 0.8 mm nozzles"
            );
        }

        if (layerHeight != null && layerHeight.compareTo(TECHNICAL_MATERIAL_MIN_LAYER_HEIGHT) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Technical materials require a layer height of at least 0.12 mm"
            );
        }
    }
}
