package com.printcalculator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.printcalculator.model.ModelDimensions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SlicerServiceTest {

    @Test
    void stripKnownModelExtension_handlesStlAnd3mf() {
        assertEquals("part", SlicerService.stripKnownModelExtension("part.stl"));
        assertEquals("plate-layout", SlicerService.stripKnownModelExtension("plate-layout.3mf"));
        assertEquals("raw", SlicerService.stripKnownModelExtension("raw"));
    }

    @Test
    void buildSliceAttempts_includesRecoveryStrategies() {
        var attempts = SlicerService.buildSliceAttempts();

        assertEquals(4, attempts.size());
        assertEquals(new SlicerService.SliceAttempt(false, false, false), attempts.get(0));
        assertEquals(new SlicerService.SliceAttempt(true, false, false), attempts.get(1));
        assertEquals(new SlicerService.SliceAttempt(true, true, false), attempts.get(2));
        assertEquals(new SlicerService.SliceAttempt(true, true, true), attempts.get(3));
    }

    @Test
    void parseModelDimensionsFromInfoOutput_validOutput_returnsDimensions() {
        String output = """
                [file.stl]
                size_x = 130.860428
                size_y = 225.000000
                size_z = 140.000000
                min_x = 0.000000
                """;

        Optional<ModelDimensions> dimensions = SlicerService.parseModelDimensionsFromInfoOutput(output);

        assertTrue(dimensions.isPresent());
        assertEquals(130.860428, dimensions.get().xMm(), 0.000001);
        assertEquals(225.0, dimensions.get().yMm(), 0.000001);
        assertEquals(140.0, dimensions.get().zMm(), 0.000001);
    }

    @Test
    void parseModelDimensionsFromInfoOutput_withNoise_returnsDimensions() {
        String output = """
                [2026-02-27 10:26:30.306251] [0x1] [trace] Initializing StaticPrintConfigs
                [model.3mf]
                size_x = 97.909241
                size_y = 97.909241
                size_z = 70.000008
                [2026-02-27 10:26:30.314575] [0x1] [error] calc_exclude_triangles
                """;

        Optional<ModelDimensions> dimensions = SlicerService.parseModelDimensionsFromInfoOutput(output);

        assertTrue(dimensions.isPresent());
        assertEquals(97.909241, dimensions.get().xMm(), 0.000001);
        assertEquals(97.909241, dimensions.get().yMm(), 0.000001);
        assertEquals(70.000008, dimensions.get().zMm(), 0.000001);
    }

    @Test
    void parseModelDimensionsFromInfoOutput_missingValues_returnsEmpty() {
        String output = """
                [model.step]
                size_x = 10.0
                size_y = 20.0
                """;

        Optional<ModelDimensions> dimensions = SlicerService.parseModelDimensionsFromInfoOutput(output);

        assertTrue(dimensions.isEmpty());
    }

    @Test
    void extractPrintableVolume_rectangularArea_returnsDimensions() {
        ObjectMapper mapper = new ObjectMapper();
        var machineProfile = mapper.createObjectNode();
        machineProfile.putArray("printable_area")
                .add("0x0")
                .add("256x0")
                .add("256x256")
                .add("0x256");
        machineProfile.put("printable_height", "256");

        Optional<PrintableVolume> volume = SlicerService.extractPrintableVolume(machineProfile);

        assertTrue(volume.isPresent());
        assertEquals(256.0, volume.get().xMm(), 0.000001);
        assertEquals(256.0, volume.get().yMm(), 0.000001);
        assertEquals(256.0, volume.get().zMm(), 0.000001);
    }

    @Test
    void buildOutOfVolumeMessage_excludesPrinterNameAndIncludesModelAndPrinterLimits() {
        String message = SlicerService.buildOutOfVolumeMessage(
                "Bambu Lab A1 0.4 nozzle",
                Optional.of(new ModelDimensions(256.0, 256.0, 256.0)),
                Optional.of(new PrintableVolume(256.0, 256.0, 256.0))
        );

        assertFalse(message.contains("Bambu Lab A1 0.4 nozzle"));
        assertTrue(message.contains("model size 256 x 256 x 256 mm"));
        assertTrue(message.contains("printer limit 256 x 256 x 256 mm"));
        assertTrue(message.contains("Accept split printing to calculate it anyway"));
    }
}
