package com.printcalculator.service.quote;

import com.printcalculator.dto.PrintSettingsDto;
import com.printcalculator.entity.FilamentMaterialType;
import com.printcalculator.entity.FilamentVariant;
import com.printcalculator.entity.PrinterMachine;
import com.printcalculator.entity.QuoteLineItem;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.exception.ModelProcessingException;
import com.printcalculator.model.ModelDimensions;
import com.printcalculator.model.PrintStats;
import com.printcalculator.model.QuoteResult;
import com.printcalculator.repository.QuoteLineItemRepository;
import com.printcalculator.repository.QuoteSessionRepository;
import com.printcalculator.service.MaterialPrintCompatibilityService;
import com.printcalculator.service.OrcaProfileResolver;
import com.printcalculator.service.ProfileManager;
import com.printcalculator.service.QuoteCalculator;
import com.printcalculator.service.SlicerService;
import com.printcalculator.service.storage.ClamAVService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteSessionItemServiceTest {

    @Mock
    private QuoteLineItemRepository lineItemRepo;
    @Mock
    private QuoteSessionRepository sessionRepo;
    @Mock
    private SlicerService slicerService;
    @Mock
    private QuoteCalculator quoteCalculator;
    @Mock
    private OrcaProfileResolver orcaProfileResolver;
    @Mock
    private ClamAVService clamAVService;
    @Mock
    private QuoteStorageService quoteStorageService;
    @Mock
    private QuoteSessionSettingsService settingsService;
    @Mock
    private ProfileManager profileManager;
    @Mock
    private MaterialPrintCompatibilityService materialPrintCompatibilityService;

    @TempDir
    Path tempDir;

    private QuoteSessionItemService service;

    @BeforeEach
    void setUp() {
        service = new QuoteSessionItemService(
                lineItemRepo,
                sessionRepo,
                slicerService,
                quoteCalculator,
                orcaProfileResolver,
                clamAVService,
                quoteStorageService,
                settingsService,
                profileManager,
                materialPrintCompatibilityService
        );
    }

    @Test
    void addItemToSession_with3mfSlicesOriginalProjectAndFallsBackToConvertedPreviewDimensions() throws Exception {
        QuoteSession session = new QuoteSession();
        session.setId(UUID.randomUUID());
        session.setStatus("ACTIVE");

        PrintSettingsDto settings = new PrintSettingsDto();
        settings.setComplexityMode("ADVANCED");
        settings.setQuantity(2);
        settings.setSupportsEnabled(true);
        settings.setInfillDensity(15.0);
        settings.setInfillPattern("grid");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fixture.3mf",
                "application/octet-stream",
                "dummy-3mf".getBytes(StandardCharsets.UTF_8)
        );

        FilamentMaterialType materialType = new FilamentMaterialType();
        materialType.setMaterialCode("PLA");

        FilamentVariant variant = new FilamentVariant();
        variant.setFilamentMaterialType(materialType);
        variant.setColorName("White");

        PrinterMachine machine = new PrinterMachine();
        machine.setPrinterDisplayName("BambuLab A1");

        PrintStats stats = new PrintStats(3600, "1h", 42.0, 1000.0);
        QuoteResult result = new QuoteResult(12.5, "CHF", stats);

        when(quoteStorageService.getSafeExtension(file.getOriginalFilename(), "")).thenReturn("3mf");
        when(quoteStorageService.sessionStorageDir(session.getId())).thenReturn(tempDir);
        when(quoteStorageService.resolveSessionPath(eq(tempDir), anyString()))
                .thenAnswer(invocation -> tempDir.resolve(invocation.getArgument(1, String.class)));
        when(quoteStorageService.toStoredPath(any(Path.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Path.class).toString());

        when(clamAVService.scan(any(InputStream.class))).thenReturn(true);
        doNothing().when(settingsService).applyPrintSettings(settings);

        BigDecimal nozzle = new BigDecimal("0.40");
        BigDecimal layer = new BigDecimal("0.200");
        when(settingsService.resolveNozzleAndLayer(settings))
                .thenReturn(new QuoteSessionSettingsService.NozzleLayerSettings(nozzle, layer));
        when(settingsService.resolveFilamentVariant(settings)).thenReturn(variant);
        when(settingsService.resolveQuality(settings, layer)).thenReturn("standard");
        when(settingsService.resolvePrinterMachineCandidates(settings.getPrinterMachineId(), nozzle, layer, "standard"))
                .thenReturn(java.util.List.of(machine));
        when(profileManager.findCompatibleProcessProfileName("Bambu Lab A1 0.4 nozzle", layer, "standard"))
                .thenReturn(Optional.of("0.20mm Standard @BBL A1"));
        when(orcaProfileResolver.resolve(machine, nozzle, variant))
                .thenReturn(new OrcaProfileResolver.ResolvedProfiles(
                        "Bambu Lab A1 0.4 nozzle",
                        "Bambu PLA Basic @BBL A1",
                        null
                ));

        when(slicerService.convert3mfToPersistentStl(any(File.class), any(Path.class)))
                .thenAnswer(invocation -> invocation.getArgument(1, Path.class));
        when(slicerService.slice(
                any(File.class),
                eq("Bambu Lab A1 0.4 nozzle"),
                eq("Bambu PLA Basic @BBL A1"),
                eq("0.20mm Standard @BBL A1"),
                isNull(),
                anyMap()
        )).thenReturn(stats);
        when(slicerService.inspectModelDimensions(any(File.class)))
                .thenAnswer(invocation -> {
                    File inspected = invocation.getArgument(0, File.class);
                    if (inspected.getName().endsWith(".3mf")) {
                        return Optional.empty();
                    }
                    return Optional.of(new ModelDimensions(120.0, 80.0, 25.0));
                });
        when(quoteCalculator.calculate(stats, "BambuLab A1", variant)).thenReturn(result);
        when(sessionRepo.save(any(QuoteSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lineItemRepo.save(any(QuoteLineItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QuoteLineItem saved = service.addItemToSession(session, file, settings);

        verify(materialPrintCompatibilityService).validate(variant, nozzle, layer);

        ArgumentCaptor<File> sliceInputCaptor = ArgumentCaptor.forClass(File.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> processOverridesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(slicerService).slice(
                sliceInputCaptor.capture(),
                eq("Bambu Lab A1 0.4 nozzle"),
                eq("Bambu PLA Basic @BBL A1"),
                eq("0.20mm Standard @BBL A1"),
                isNull(),
                processOverridesCaptor.capture()
        );

        assertTrue(sliceInputCaptor.getValue().getName().endsWith(".3mf"));
        assertEquals("1", processOverridesCaptor.getValue().get("enable_support"));

        ArgumentCaptor<File> inspectCaptor = ArgumentCaptor.forClass(File.class);
        verify(slicerService, times(2)).inspectModelDimensions(inspectCaptor.capture());
        assertTrue(inspectCaptor.getAllValues().get(0).getName().endsWith(".3mf"));
        assertTrue(inspectCaptor.getAllValues().get(1).getName().endsWith(".stl"));

        assertEquals(0, BigDecimal.valueOf(120.0).compareTo(saved.getBoundingBoxXMm()));
        assertEquals(0, BigDecimal.valueOf(80.0).compareTo(saved.getBoundingBoxYMm()));
        assertEquals(0, BigDecimal.valueOf(25.0).compareTo(saved.getBoundingBoxZMm()));
        assertFalse(Boolean.TRUE.equals(saved.getRequiresSplitPrinting()));
    }

    @Test
    void addItemToSession_withOversizedModel_preservesFileForManualReview() throws Exception {
        QuoteSession session = new QuoteSession();
        session.setId(UUID.randomUUID());
        session.setStatus("ACTIVE");

        PrintSettingsDto settings = new PrintSettingsDto();
        settings.setComplexityMode("ADVANCED");
        settings.setQuantity(1);
        settings.setSupportsEnabled(false);
        settings.setInfillDensity(15.0);
        settings.setInfillPattern("grid");
        settings.setAllowSplitForOversized(false);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.stl",
                "model/stl",
                "solid large".getBytes(StandardCharsets.UTF_8)
        );

        FilamentMaterialType materialType = new FilamentMaterialType();
        materialType.setMaterialCode("PLA");

        FilamentVariant variant = new FilamentVariant();
        variant.setFilamentMaterialType(materialType);
        variant.setColorName("White");

        PrinterMachine machine = new PrinterMachine();
        machine.setPrinterDisplayName("BambuLab A1");

        BigDecimal nozzle = new BigDecimal("0.40");
        BigDecimal layer = new BigDecimal("0.200");
        ModelDimensions dimensions = new ModelDimensions(188.0, 385.0, 117.0);
        when(quoteStorageService.getSafeExtension(file.getOriginalFilename(), "")).thenReturn("stl");
        when(quoteStorageService.sessionStorageDir(session.getId())).thenReturn(tempDir);
        when(quoteStorageService.resolveSessionPath(eq(tempDir), anyString()))
                .thenAnswer(invocation -> tempDir.resolve(invocation.getArgument(1, String.class)));
        when(quoteStorageService.toStoredPath(any(Path.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Path.class).toString());
        when(clamAVService.scan(any(InputStream.class))).thenReturn(true);
        doNothing().when(settingsService).applyPrintSettings(settings);
        when(settingsService.resolveNozzleAndLayer(settings))
                .thenReturn(new QuoteSessionSettingsService.NozzleLayerSettings(nozzle, layer));
        when(settingsService.resolveFilamentVariant(settings)).thenReturn(variant);
        when(settingsService.resolveQuality(settings, layer)).thenReturn("standard");
        when(settingsService.resolvePrinterMachineCandidates(settings.getPrinterMachineId(), nozzle, layer, "standard"))
                .thenReturn(java.util.List.of(machine));
        when(profileManager.findCompatibleProcessProfileName("Bambu Lab A1 0.4 nozzle", layer, "standard"))
                .thenReturn(Optional.of("0.20mm Standard @BBL A1"));
        when(orcaProfileResolver.resolve(machine, nozzle, variant))
                .thenReturn(new OrcaProfileResolver.ResolvedProfiles(
                        "Bambu Lab A1 0.4 nozzle",
                        "Bambu PLA Basic @BBL A1",
                        null
                ));
        when(slicerService.inspectModelDimensions(any(File.class))).thenReturn(Optional.of(dimensions));
        when(slicerService.slice(
                any(File.class),
                eq("Bambu Lab A1 0.4 nozzle"),
                eq("Bambu PLA Basic @BBL A1"),
                eq("0.20mm Standard @BBL A1"),
                isNull(),
                anyMap()
        )).thenThrow(new ModelProcessingException("MODEL_OUT_OF_PRINT_VOLUME", "too large"));
        when(sessionRepo.save(any(QuoteSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lineItemRepo.save(any(QuoteLineItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QuoteLineItem saved = service.addItemToSession(session, file, settings);

        assertFalse(Boolean.TRUE.equals(saved.getRequiresSplitPrinting()));
        assertEquals("REVIEW_REQUIRED", saved.getStatus());
        assertEquals("too large", saved.getErrorMessage());
        assertEquals("MODEL_OUT_OF_PRINT_VOLUME", saved.getPricingBreakdown().get("errorCode"));
        verify(slicerService, never()).sliceForOversizedEstimate(
                any(File.class), anyString(), anyString(), anyString(), any(), anyMap());
    }

    @Test
    void addItemToSession_withAcceptedSplitPrinting_createsOrderableEstimate() throws Exception {
        QuoteSession session = new QuoteSession();
        session.setId(UUID.randomUUID());
        session.setStatus("ACTIVE");

        PrintSettingsDto settings = new PrintSettingsDto();
        settings.setComplexityMode("ADVANCED");
        settings.setQuantity(1);
        settings.setSupportsEnabled(false);
        settings.setInfillDensity(15.0);
        settings.setInfillPattern("grid");
        settings.setAllowSplitForOversized(true);

        MockMultipartFile file = new MockMultipartFile(
                "file", "large.stl", "model/stl", "solid large".getBytes(StandardCharsets.UTF_8));

        FilamentMaterialType materialType = new FilamentMaterialType();
        materialType.setMaterialCode("PLA");
        FilamentVariant variant = new FilamentVariant();
        variant.setFilamentMaterialType(materialType);
        variant.setColorName("White");
        PrinterMachine machine = new PrinterMachine();
        machine.setPrinterDisplayName("BambuLab A1");

        BigDecimal nozzle = new BigDecimal("0.40");
        BigDecimal layer = new BigDecimal("0.200");
        ModelDimensions dimensions = new ModelDimensions(188.0, 385.0, 117.0);
        PrintStats stats = new PrintStats(7200, "2h", 84.0, 1000.0);
        QuoteResult result = new QuoteResult(25.0, "CHF", stats);

        when(quoteStorageService.getSafeExtension(file.getOriginalFilename(), "")).thenReturn("stl");
        when(quoteStorageService.sessionStorageDir(session.getId())).thenReturn(tempDir);
        when(quoteStorageService.resolveSessionPath(eq(tempDir), anyString()))
                .thenAnswer(invocation -> tempDir.resolve(invocation.getArgument(1, String.class)));
        when(quoteStorageService.toStoredPath(any(Path.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Path.class).toString());
        when(clamAVService.scan(any(InputStream.class))).thenReturn(true);
        doNothing().when(settingsService).applyPrintSettings(settings);
        when(settingsService.resolveNozzleAndLayer(settings))
                .thenReturn(new QuoteSessionSettingsService.NozzleLayerSettings(nozzle, layer));
        when(settingsService.resolveFilamentVariant(settings)).thenReturn(variant);
        when(settingsService.resolveQuality(settings, layer)).thenReturn("standard");
        when(settingsService.resolvePrinterMachineCandidates(settings.getPrinterMachineId(), nozzle, layer, "standard"))
                .thenReturn(java.util.List.of(machine));
        when(profileManager.findCompatibleProcessProfileName("Bambu Lab A1 0.4 nozzle", layer, "standard"))
                .thenReturn(Optional.of("0.20mm Standard @BBL A1"));
        when(orcaProfileResolver.resolve(machine, nozzle, variant))
                .thenReturn(new OrcaProfileResolver.ResolvedProfiles(
                        "Bambu Lab A1 0.4 nozzle", "Bambu PLA Basic @BBL A1", null));
        when(slicerService.inspectModelDimensions(any(File.class))).thenReturn(Optional.of(dimensions));
        when(slicerService.slice(
                any(File.class),
                eq("Bambu Lab A1 0.4 nozzle"),
                eq("Bambu PLA Basic @BBL A1"),
                eq("0.20mm Standard @BBL A1"),
                isNull(),
                anyMap()
        )).thenThrow(new ModelProcessingException("MODEL_OUT_OF_PRINT_VOLUME", "too large"));
        when(slicerService.sliceForOversizedEstimate(
                any(File.class),
                eq("Bambu Lab A1 0.4 nozzle"),
                eq("Bambu PLA Basic @BBL A1"),
                eq("0.20mm Standard @BBL A1"),
                eq(Optional.of(dimensions)),
                anyMap()
        )).thenReturn(stats);
        when(quoteCalculator.calculate(stats, "BambuLab A1", variant)).thenReturn(result);
        when(sessionRepo.save(any(QuoteSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lineItemRepo.save(any(QuoteLineItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QuoteLineItem saved = service.addItemToSession(session, file, settings);

        assertEquals("READY", saved.getStatus());
        assertTrue(Boolean.TRUE.equals(saved.getRequiresSplitPrinting()));
        assertEquals(0, BigDecimal.valueOf(188.0).compareTo(saved.getBoundingBoxXMm()));
        verify(slicerService).sliceForOversizedEstimate(
                any(File.class),
                eq("Bambu Lab A1 0.4 nozzle"),
                eq("Bambu PLA Basic @BBL A1"),
                eq("0.20mm Standard @BBL A1"),
                eq(Optional.of(dimensions)),
                anyMap()
        );
    }
}
