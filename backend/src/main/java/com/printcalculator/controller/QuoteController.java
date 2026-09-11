package com.printcalculator.controller;

import com.printcalculator.entity.PrinterMachine;
import com.printcalculator.model.PrintStats;
import com.printcalculator.model.QuoteResult;
import com.printcalculator.repository.PrinterMachineRepository;
import com.printcalculator.service.NozzleLayerHeightPolicyService;
import com.printcalculator.service.QuoteCalculator;
import com.printcalculator.service.QuoteRateLimitService;
import com.printcalculator.service.SlicerService;
import com.printcalculator.service.storage.ClamAVService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
public class QuoteController {

    private final SlicerService slicerService;
    private final QuoteCalculator quoteCalculator;
    private final PrinterMachineRepository machineRepo;
    private final ClamAVService clamAVService;
    private final NozzleLayerHeightPolicyService nozzleLayerHeightPolicyService;
    private final QuoteRateLimitService quoteRateLimitService;

    // Defaults (using aliases defined in ProfileManager)
    private static final String DEFAULT_FILAMENT = "pla_basic";
    private static final String DEFAULT_PROCESS = "standard";

    public QuoteController(SlicerService slicerService,
                           QuoteCalculator quoteCalculator,
                           PrinterMachineRepository machineRepo,
                           ClamAVService clamAVService,
                           NozzleLayerHeightPolicyService nozzleLayerHeightPolicyService,
                           QuoteRateLimitService quoteRateLimitService) {
        this.slicerService = slicerService;
        this.quoteCalculator = quoteCalculator;
        this.machineRepo = machineRepo;
        this.clamAVService = clamAVService;
        this.nozzleLayerHeightPolicyService = nozzleLayerHeightPolicyService;
        this.quoteRateLimitService = quoteRateLimitService;
    }

    @PostMapping("/api/quote")
    public ResponseEntity<QuoteResult> calculateQuote(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "filament", required = false, defaultValue = DEFAULT_FILAMENT) String filament,
            @RequestParam(value = "process", required = false) String process,
            @RequestParam(value = "quality", required = false) String quality,
            // Advanced Options
            @RequestParam(value = "infill_density", required = false) Integer infillDensity,
            @RequestParam(value = "infill_pattern", required = false) String infillPattern,
            @RequestParam(value = "layer_height", required = false) Double layerHeight,
            @RequestParam(value = "nozzle_diameter", required = false) Double nozzleDiameter,
            @RequestParam(value = "support_enabled", required = false) Boolean supportEnabled,
            HttpServletRequest request
            ) throws IOException {

        quoteRateLimitService.checkAllowed(request);

        // ... process selection logic ...
        String actualProcess = process;
        if (actualProcess == null || actualProcess.isEmpty()) {
            if (quality != null && !quality.isEmpty()) {
                actualProcess = quality;
            } else {
                actualProcess = DEFAULT_PROCESS;
            }
        }

        // Prepare Overrides
        Map<String, String> processOverrides = new HashMap<>();
        Map<String, String> machineOverrides = new HashMap<>();

        if (infillDensity != null) {
            processOverrides.put("sparse_infill_density", infillDensity + "%");
        }
        if (infillPattern != null && !infillPattern.isEmpty()) {
            processOverrides.put("sparse_infill_pattern", infillPattern);
        }
        BigDecimal normalizedNozzle = nozzleLayerHeightPolicyService.resolveNozzle(
                nozzleDiameter != null ? BigDecimal.valueOf(nozzleDiameter) : null
        );
        if (layerHeight != null) {
            BigDecimal normalizedLayer = nozzleLayerHeightPolicyService.normalizeLayer(BigDecimal.valueOf(layerHeight));
            if (!nozzleLayerHeightPolicyService.isAllowed(normalizedNozzle, normalizedLayer)) {
                throw new ResponseStatusException(
                        BAD_REQUEST,
                        "Layer height " + normalizedLayer.stripTrailingZeros().toPlainString()
                                + " is not allowed for nozzle " + normalizedNozzle.stripTrailingZeros().toPlainString()
                                + ". Allowed: " + nozzleLayerHeightPolicyService.allowedLayersLabel(normalizedNozzle)
                );
            }
            processOverrides.put("layer_height", normalizedLayer.stripTrailingZeros().toPlainString());
        }
        if (supportEnabled != null) {
            processOverrides.put("enable_support", supportEnabled ? "1" : "0");
        }

        if (nozzleDiameter != null) {
            machineOverrides.put("nozzle_diameter", normalizedNozzle.stripTrailingZeros().toPlainString());
            // Also need to ensure the printer profile is compatible or just override?
            // Usually nozzle diameter changes require a different printer profile or deep overrides.
            // For now, we trust the override key works on the base profile.
        }

        return processRequest(file, filament, actualProcess, machineOverrides, processOverrides);
    }

    @PostMapping("/calculate/stl")
    public ResponseEntity<QuoteResult> legacyCalculate(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request
    ) throws IOException {
        quoteRateLimitService.checkAllowed(request);
        // Legacy endpoint uses defaults
        return processRequest(file, DEFAULT_FILAMENT, DEFAULT_PROCESS, null, null);
    }

    private ResponseEntity<QuoteResult> processRequest(MultipartFile file, String filament, String process,
                                                       Map<String, String> machineOverrides,
                                                       Map<String, String> processOverrides) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (!isSupportedInputFile(file)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported file type. Allowed: stl, 3mf");
        }

        // Scan for virus
        clamAVService.scan(file.getInputStream());

        // Fetch Default Active Machine
        PrinterMachine machine = machineRepo.findFirstByIsActiveTrueOrderByIdAsc()
                .orElseThrow(() -> new IOException("No active printer found in database"));

        // Save uploaded file temporarily
        Path tempInput = Files.createTempFile("upload_", "_" + file.getOriginalFilename());
        try {
            file.transferTo(tempInput.toFile());

            String slicerMachineProfile = "bambu_a1"; // TODO: Add to PrinterMachine entity

            PrintStats stats = slicerService.slice(tempInput.toFile(), slicerMachineProfile, filament, process, machineOverrides, processOverrides);
            
            // Calculate Quote (Pass machine display name for pricing lookup)
            QuoteResult result = quoteCalculator.calculate(stats, machine.getPrinterDisplayName(), filament);
            
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        } finally {
            Files.deleteIfExists(tempInput);
        }
    }

    private boolean isSupportedInputFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            return false;
        }

        String normalized = originalFilename.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".stl") || normalized.endsWith(".3mf");
    }
}
