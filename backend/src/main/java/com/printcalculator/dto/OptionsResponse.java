package com.printcalculator.dto;

import java.util.List;

public record OptionsResponse(
    List<MaterialOption> materials,
    List<QualityOption> qualities,
    List<InfillPatternOption> infillPatterns,
    List<LayerHeightOptionDTO> layerHeights,
    List<NozzleOptionDTO> nozzleDiameters,
    List<NozzleLayerHeightOptionsDTO> layerHeightsByNozzle
) {
    public record MaterialOption(String code, String label, boolean isTechnical, List<VariantOption> variants) {}
    public record VariantOption(
            Long id,
            String name,
            String colorName,
            String colorLabelIt,
            String colorLabelEn,
            String colorLabelDe,
            String colorLabelFr,
            String hexColor,
            String finishType,
            Double stockSpools,
            Double stockFilamentGrams,
            boolean isOutOfStock
    ) {}
    public record QualityOption(String id, String label) {}
    public record InfillPatternOption(String id, String label) {}
    public record LayerHeightOptionDTO(double value, String label) {}
    public record NozzleOptionDTO(double value, String label) {}
    public record NozzleLayerHeightOptionsDTO(double nozzleDiameter, List<LayerHeightOptionDTO> layerHeights) {}
}
