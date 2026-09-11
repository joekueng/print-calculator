package com.printcalculator.controller;

import com.printcalculator.dto.OptionsResponse;
import com.printcalculator.entity.FilamentMaterialType;
import com.printcalculator.entity.FilamentVariant;
import com.printcalculator.entity.MaterialOrcaProfileMap;
import com.printcalculator.entity.NozzleOption;
import com.printcalculator.entity.PrinterMachine;
import com.printcalculator.entity.PrinterMachineProfile;
import com.printcalculator.repository.FilamentMaterialTypeRepository;
import com.printcalculator.repository.FilamentVariantRepository;
import com.printcalculator.repository.MaterialOrcaProfileMapRepository;
import com.printcalculator.repository.NozzleOptionRepository;
import com.printcalculator.repository.PrinterMachineRepository;
import com.printcalculator.repository.PrinterMachineProfileRepository;
import com.printcalculator.service.NozzleLayerHeightPolicyService;
import com.printcalculator.service.OrcaProfileResolver;
import com.printcalculator.service.ProfileManager;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class OptionsController {

    private final FilamentMaterialTypeRepository materialRepo;
    private final FilamentVariantRepository variantRepo;
    private final NozzleOptionRepository nozzleRepo;
    private final PrinterMachineRepository printerMachineRepo;
    private final PrinterMachineProfileRepository printerMachineProfileRepo;
    private final MaterialOrcaProfileMapRepository materialOrcaMapRepo;
    private final OrcaProfileResolver orcaProfileResolver;
    private final ProfileManager profileManager;
    private final NozzleLayerHeightPolicyService nozzleLayerHeightPolicyService;

    public OptionsController(FilamentMaterialTypeRepository materialRepo,
                             FilamentVariantRepository variantRepo,
                             NozzleOptionRepository nozzleRepo,
                             PrinterMachineRepository printerMachineRepo,
                             PrinterMachineProfileRepository printerMachineProfileRepo,
                             MaterialOrcaProfileMapRepository materialOrcaMapRepo,
                             OrcaProfileResolver orcaProfileResolver,
                             ProfileManager profileManager,
                             NozzleLayerHeightPolicyService nozzleLayerHeightPolicyService) {
        this.materialRepo = materialRepo;
        this.variantRepo = variantRepo;
        this.nozzleRepo = nozzleRepo;
        this.printerMachineRepo = printerMachineRepo;
        this.printerMachineProfileRepo = printerMachineProfileRepo;
        this.materialOrcaMapRepo = materialOrcaMapRepo;
        this.orcaProfileResolver = orcaProfileResolver;
        this.profileManager = profileManager;
        this.nozzleLayerHeightPolicyService = nozzleLayerHeightPolicyService;
    }

    @GetMapping("/api/calculator/options")
    @Transactional(readOnly = true)
    public ResponseEntity<OptionsResponse> getOptions(
            @RequestParam(value = "printerMachineId", required = false) Long printerMachineId,
            @RequestParam(value = "nozzleDiameter", required = false) Double nozzleDiameter
    ) {
        List<FilamentMaterialType> types = materialRepo.findAll();
        List<FilamentVariant> allVariants = variantRepo.findByIsActiveTrue().stream()
                .sorted(Comparator
                        .comparing((FilamentVariant v) -> safeMaterialCode(v.getFilamentMaterialType()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(v -> safeString(v.getVariantDisplayName()), String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<PrinterMachine> targetMachines = resolveMachines(printerMachineId);
        Set<Long> compatibleMaterialTypeIds = resolveCompatibleMaterialTypeIds(targetMachines, nozzleDiameter);

        List<OptionsResponse.MaterialOption> materialOptions = types.stream()
                .sorted(Comparator.comparing(t -> safeString(t.getMaterialCode()), String.CASE_INSENSITIVE_ORDER))
                .map(type -> {
                    if (!compatibleMaterialTypeIds.isEmpty() && !compatibleMaterialTypeIds.contains(type.getId())) {
                        return null;
                    }

                    List<OptionsResponse.VariantOption> variants = allVariants.stream()
                            .filter(v -> v.getFilamentMaterialType() != null
                                    && v.getFilamentMaterialType().getId().equals(type.getId()))
                            .map(v -> new OptionsResponse.VariantOption(
                                    v.getId(),
                                    v.getVariantDisplayName(),
                                    v.getColorName(),
                                    v.getColorLabelIt(),
                                    v.getColorLabelEn(),
                                    v.getColorLabelDe(),
                                    v.getColorLabelFr(),
                                    resolveHexColor(v),
                                    v.getFinishType() != null ? v.getFinishType() : "GLOSSY",
                                    v.getStockSpools() != null ? v.getStockSpools().doubleValue() : 0d,
                                    toStockFilamentGrams(v),
                                    v.getStockSpools() == null || v.getStockSpools().doubleValue() <= 0
                            ))
                            .collect(Collectors.toList());

                    if (variants.isEmpty()) {
                        return null;
                    }

                    return new OptionsResponse.MaterialOption(
                            type.getMaterialCode(),
                            buildMaterialLabel(type),
                            Boolean.TRUE.equals(type.getIsTechnical()),
                            variants
                    );
                })
                .filter(m -> m != null)
                .toList();

        List<OptionsResponse.QualityOption> qualities = List.of(
                new OptionsResponse.QualityOption("draft", "Draft"),
                new OptionsResponse.QualityOption("standard", "Standard"),
                new OptionsResponse.QualityOption("extra_fine", "High Definition")
        );

        List<OptionsResponse.InfillPatternOption> patterns = List.of(
                new OptionsResponse.InfillPatternOption("grid", "Grid"),
                new OptionsResponse.InfillPatternOption("gyroid", "Gyroid"),
                new OptionsResponse.InfillPatternOption("cubic", "Cubic")
        );

        Set<BigDecimal> supportedMachineNozzles = targetMachines.stream()
                .flatMap(machine -> printerMachineProfileRepo.findByPrinterMachineAndIsActiveTrue(machine).stream())
                .map(PrinterMachineProfile::getNozzleDiameterMm)
                .filter(v -> v != null)
                .map(nozzleLayerHeightPolicyService::normalizeNozzle)
                .filter(v -> v != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        boolean restrictNozzlesByMachineProfile = !supportedMachineNozzles.isEmpty();

        List<OptionsResponse.NozzleOptionDTO> nozzles = nozzleRepo.findAll().stream()
                .filter(n -> Boolean.TRUE.equals(n.getIsActive()))
                .filter(n -> {
                    if (!restrictNozzlesByMachineProfile) {
                        return true;
                    }
                    BigDecimal normalized = nozzleLayerHeightPolicyService.normalizeNozzle(n.getNozzleDiameterMm());
                    return normalized != null && supportedMachineNozzles.contains(normalized);
                })
                .sorted(Comparator.comparing(NozzleOption::getNozzleDiameterMm))
                .map(n -> new OptionsResponse.NozzleOptionDTO(
                        n.getNozzleDiameterMm().doubleValue(),
                        String.format("%.1f mm%s", n.getNozzleDiameterMm(),
                                n.getExtraNozzleChangeFeeChf().doubleValue() > 0
                                        ? String.format(" (+ %.2f CHF)", n.getExtraNozzleChangeFeeChf())
                                        : " (Standard)")
                ))
                .toList();

        Map<BigDecimal, List<BigDecimal>> rulesByNozzle = nozzleLayerHeightPolicyService.getActiveRulesByNozzle();
        Set<BigDecimal> visibleNozzlesFromOptions = nozzles.stream()
                .map(OptionsResponse.NozzleOptionDTO::value)
                .map(BigDecimal::valueOf)
                .map(nozzleLayerHeightPolicyService::normalizeNozzle)
                .filter(v -> v != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<BigDecimal, List<BigDecimal>> effectiveRulesByNozzle = new LinkedHashMap<>();
        for (BigDecimal nozzle : visibleNozzlesFromOptions) {
            List<BigDecimal> policyLayers = rulesByNozzle.getOrDefault(nozzle, List.of());
            Set<BigDecimal> aggregate = new LinkedHashSet<>();
            for (PrinterMachine machine : targetMachines) {
                List<BigDecimal> compatibleProcessLayers = resolveCompatibleProcessLayers(machine, nozzle);
                List<BigDecimal> effective = mergePolicyAndProcessLayers(policyLayers, compatibleProcessLayers);
                effective.stream()
                        .map(nozzleLayerHeightPolicyService::normalizeLayer)
                        .filter(v -> v != null)
                        .forEach(aggregate::add);
            }
            if (!aggregate.isEmpty()) {
                List<BigDecimal> merged = new ArrayList<>(aggregate);
                merged.sort(Comparator.naturalOrder());
                effectiveRulesByNozzle.put(nozzle, merged);
            }
        }
        if (effectiveRulesByNozzle.isEmpty()) {
            for (BigDecimal nozzle : visibleNozzlesFromOptions) {
                List<BigDecimal> policyLayers = rulesByNozzle.getOrDefault(nozzle, List.of());
                if (!policyLayers.isEmpty()) {
                    effectiveRulesByNozzle.put(nozzle, policyLayers);
                }
            }
        }

        Set<BigDecimal> visibleNozzles = new LinkedHashSet<>(effectiveRulesByNozzle.keySet());
        nozzles = nozzles.stream()
                .filter(option -> {
                    BigDecimal normalized = nozzleLayerHeightPolicyService.normalizeNozzle(
                            BigDecimal.valueOf(option.value())
                    );
                    return normalized != null && visibleNozzles.contains(normalized);
                })
                .toList();

        BigDecimal selectedNozzle = nozzleLayerHeightPolicyService.resolveNozzle(
                nozzleDiameter != null ? BigDecimal.valueOf(nozzleDiameter) : null
        );
        if (!visibleNozzles.isEmpty() && !visibleNozzles.contains(selectedNozzle)) {
            selectedNozzle = visibleNozzles.iterator().next();
        }

        List<OptionsResponse.LayerHeightOptionDTO> layers = toLayerDtos(
                effectiveRulesByNozzle.getOrDefault(selectedNozzle, List.of())
        );
        if (layers.isEmpty()) {
            if (!visibleNozzles.isEmpty()) {
                BigDecimal fallbackNozzle = visibleNozzles.iterator().next();
                layers = toLayerDtos(effectiveRulesByNozzle.getOrDefault(fallbackNozzle, List.of()));
            }
            if (layers.isEmpty()) {
                layers = rulesByNozzle.values().stream().findFirst().map(this::toLayerDtos).orElse(List.of());
            }
        }

        List<OptionsResponse.NozzleLayerHeightOptionsDTO> layerHeightsByNozzle = effectiveRulesByNozzle.entrySet().stream()
                .map(entry -> new OptionsResponse.NozzleLayerHeightOptionsDTO(
                        entry.getKey().doubleValue(),
                        toLayerDtos(entry.getValue())
                ))
                .toList();

        return ResponseEntity.ok(new OptionsResponse(
                materialOptions,
                qualities,
                patterns,
                layers,
                nozzles,
                layerHeightsByNozzle
        ));
    }

    private Set<Long> resolveCompatibleMaterialTypeIds(List<PrinterMachine> machines, Double nozzleDiameter) {
        if (machines == null || machines.isEmpty()) {
            return Set.of();
        }

        BigDecimal requestedNozzle = nozzleDiameter != null
                ? nozzleLayerHeightPolicyService.normalizeNozzle(BigDecimal.valueOf(nozzleDiameter))
                : null;

        Set<Long> materialTypeIds = new LinkedHashSet<>();
        for (PrinterMachine machine : machines) {
            List<PrinterMachineProfile> profiles = printerMachineProfileRepo.findByPrinterMachineAndIsActiveTrue(machine);
            for (PrinterMachineProfile profile : profiles) {
                BigDecimal profileNozzle = nozzleLayerHeightPolicyService.normalizeNozzle(profile.getNozzleDiameterMm());
                if (requestedNozzle != null && (profileNozzle == null || profileNozzle.compareTo(requestedNozzle) != 0)) {
                    continue;
                }

                List<MaterialOrcaProfileMap> maps = materialOrcaMapRepo.findByPrinterMachineProfileAndIsActiveTrue(profile);
                maps.stream()
                        .map(MaterialOrcaProfileMap::getFilamentMaterialType)
                        .filter(m -> m != null && m.getId() != null)
                        .map(FilamentMaterialType::getId)
                        .forEach(materialTypeIds::add);
            }
        }

        return materialTypeIds;
    }

    private List<PrinterMachine> resolveMachines(Long printerMachineId) {
        if (printerMachineId != null) {
            PrinterMachine machine = printerMachineRepo.findById(printerMachineId).orElse(null);
            if (machine == null || !Boolean.TRUE.equals(machine.getIsActive())) {
                return List.of();
            }
            return List.of(machine);
        }
        return printerMachineRepo.findByIsActiveTrueOrderByIdAsc();
    }

    private List<OptionsResponse.LayerHeightOptionDTO> toLayerDtos(List<BigDecimal> layers) {
        return layers.stream()
                .sorted(Comparator.naturalOrder())
                .map(layer -> new OptionsResponse.LayerHeightOptionDTO(
                        layer.doubleValue(),
                        String.format("%.2f mm", layer)
                ))
                .toList();
    }

    private List<BigDecimal> resolveCompatibleProcessLayers(PrinterMachine machine, BigDecimal nozzle) {
        if (machine == null || nozzle == null) {
            return List.of();
        }
        PrinterMachineProfile profile = orcaProfileResolver.resolveMachineProfile(machine, nozzle).orElse(null);
        if (profile == null || profile.getOrcaMachineProfileName() == null) {
            return List.of();
        }
        return profileManager.findCompatibleProcessLayers(profile.getOrcaMachineProfileName());
    }

    private List<BigDecimal> mergePolicyAndProcessLayers(List<BigDecimal> policyLayers,
                                                         List<BigDecimal> processLayers) {
        if ((processLayers == null || processLayers.isEmpty())
                && (policyLayers == null || policyLayers.isEmpty())) {
            return List.of();
        }

        if (processLayers == null || processLayers.isEmpty()) {
            return policyLayers != null ? policyLayers : List.of();
        }

        if (policyLayers == null || policyLayers.isEmpty()) {
            return processLayers;
        }

        Set<BigDecimal> allowedByPolicy = policyLayers.stream()
                .map(nozzleLayerHeightPolicyService::normalizeLayer)
                .filter(v -> v != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<BigDecimal> intersection = processLayers.stream()
                .map(nozzleLayerHeightPolicyService::normalizeLayer)
                .filter(v -> v != null && allowedByPolicy.contains(v))
                .collect(Collectors.toCollection(ArrayList::new));

        if (!intersection.isEmpty()) {
            return intersection;
        }

        return processLayers.stream()
                .map(nozzleLayerHeightPolicyService::normalizeLayer)
                .filter(v -> v != null)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private String resolveHexColor(FilamentVariant variant) {
        if (variant.getColorHex() != null && !variant.getColorHex().isBlank()) {
            return variant.getColorHex();
        }
        return getColorHex(variant.getColorName());
    }

    private double toStockFilamentGrams(FilamentVariant variant) {
        if (variant.getStockSpools() == null || variant.getSpoolNetKg() == null) {
            return 0d;
        }
        return variant.getStockSpools()
                .multiply(variant.getSpoolNetKg())
                .multiply(BigDecimal.valueOf(1000))
                .doubleValue();
    }

    private String buildMaterialLabel(FilamentMaterialType type) {
        String materialCode = safeMaterialCode(type);
        String technicalLabel = type != null && type.getTechnicalTypeLabel() != null
                ? type.getTechnicalTypeLabel().trim()
                : "";
        String fallbackLabel = Boolean.TRUE.equals(type != null ? type.getIsFlexible() : null)
                ? "Flexible"
                : "Standard";
        String suffix = !technicalLabel.isBlank() ? technicalLabel : fallbackLabel;

        return materialCode + " (" + suffix + ")";
    }

    private String safeMaterialCode(FilamentMaterialType type) {
        if (type == null || type.getMaterialCode() == null) {
            return "";
        }
        return type.getMaterialCode();
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    // Temporary helper for legacy values where color hex is not yet set in DB
    private String getColorHex(String colorName) {
        if (colorName == null) {
            return "#9e9e9e";
        }
        String lower = colorName.toLowerCase();
        if (lower.contains("black") || lower.contains("nero")) return "#1a1a1a";
        if (lower.contains("white") || lower.contains("bianco")) return "#f5f5f5";
        if (lower.contains("blue") || lower.contains("blu")) return "#1976d2";
        if (lower.contains("red") || lower.contains("rosso")) return "#d32f2f";
        if (lower.contains("green") || lower.contains("verde")) return "#388e3c";
        if (lower.contains("orange") || lower.contains("arancione")) return "#ffa726";
        if (lower.contains("grey") || lower.contains("gray") || lower.contains("grigio")) {
            if (lower.contains("dark") || lower.contains("scuro")) return "#424242";
            return "#bdbdbd";
        }
        if (lower.contains("purple") || lower.contains("viola")) return "#7b1fa2";
        if (lower.contains("yellow") || lower.contains("giallo")) return "#fbc02d";
        return "#9e9e9e";
    }
}
