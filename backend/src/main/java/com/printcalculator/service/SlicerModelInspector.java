package com.printcalculator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.printcalculator.model.ModelDimensions;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SlicerModelInspector {
    private static final Logger logger = Logger.getLogger(SlicerModelInspector.class.getName());
    private static final Pattern SIZE_X_PATTERN = Pattern.compile("(?m)^\\s*size_x\\s*=\\s*([-+]?\\d+(?:\\.\\d+)?)\\s*$");
    private static final Pattern SIZE_Y_PATTERN = Pattern.compile("(?m)^\\s*size_y\\s*=\\s*([-+]?\\d+(?:\\.\\d+)?)\\s*$");
    private static final Pattern SIZE_Z_PATTERN = Pattern.compile("(?m)^\\s*size_z\\s*=\\s*([-+]?\\d+(?:\\.\\d+)?)\\s*$");
    private static final Pattern PRINTABLE_AREA_POINT_PATTERN = Pattern.compile("^\\s*([-+]?\\d+(?:\\.\\d+)?)x([-+]?\\d+(?:\\.\\d+)?)\\s*$");

    private final String trustedSlicerPath;

    SlicerModelInspector(String trustedSlicerPath) {
        this.trustedSlicerPath = trustedSlicerPath;
    }

    Optional<ModelDimensions> inspectModelDimensions(File inputModel) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("slicer_info_");
            Path infoLogPath = tempDir.resolve("orcaslicer-info.log");
            String inputModelPath = SlicerFileSupport.requireSafeArgument(
                    inputModel.getAbsolutePath(),
                    "input model path"
            );

            ProcessBuilder pb = new ProcessBuilder();
            List<String> infoCommand = pb.command();
            infoCommand.add(trustedSlicerPath);
            infoCommand.add("--info");
            infoCommand.add(inputModelPath);
            pb.directory(tempDir.toFile());
            pb.redirectErrorStream(true);
            pb.redirectOutput(infoLogPath.toFile());

            Process process = pb.start();
            boolean finished = process.waitFor(2, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                logger.warning("Model info extraction timed out for " + inputModel.getName());
                return Optional.empty();
            }

            String output = Files.exists(infoLogPath)
                    ? Files.readString(infoLogPath, StandardCharsets.UTF_8)
                    : "";

            if (process.exitValue() != 0) {
                logger.warning("OrcaSlicer --info failed (exit " + process.exitValue() + ") for "
                        + inputModel.getName() + ": " + output);
                return Optional.empty();
            }

            Optional<ModelDimensions> parsed = parseModelDimensionsFromInfoOutput(output);
            if (parsed.isEmpty()) {
                logger.warning("Could not parse size_x/size_y/size_z from OrcaSlicer --info output for "
                        + inputModel.getName() + ": " + output);
            }
            return parsed;
        } catch (Exception e) {
            logger.warning("Failed to inspect model dimensions for " + inputModel.getName() + ": " + e.getMessage());
            return Optional.empty();
        } finally {
            if (tempDir != null) {
                SlicerFileSupport.deleteRecursively(tempDir, logger);
            }
        }
    }

    static Optional<ModelDimensions> parseModelDimensionsFromInfoOutput(String output) {
        if (output == null || output.isBlank()) {
            return Optional.empty();
        }

        Double x = extractDouble(SIZE_X_PATTERN, output);
        Double y = extractDouble(SIZE_Y_PATTERN, output);
        Double z = extractDouble(SIZE_Z_PATTERN, output);
        if (x == null || y == null || z == null) {
            return Optional.empty();
        }
        if (x <= 0 || y <= 0 || z <= 0) {
            return Optional.empty();
        }
        return Optional.of(new ModelDimensions(x, y, z));
    }

    static Optional<PrintableVolume> extractPrintableVolume(JsonNode machineProfile) {
        if (machineProfile == null || machineProfile.isMissingNode()) {
            return Optional.empty();
        }

        JsonNode printableArea = machineProfile.path("printable_area");
        if (!printableArea.isArray() || printableArea.isEmpty()) {
            return Optional.empty();
        }

        Double minX = null;
        Double maxX = null;
        Double minY = null;
        Double maxY = null;

        for (JsonNode pointNode : printableArea) {
            String point = pointNode.asText(null);
            if (point == null || point.isBlank()) {
                return Optional.empty();
            }

            Matcher matcher = PRINTABLE_AREA_POINT_PATTERN.matcher(point);
            if (!matcher.matches()) {
                return Optional.empty();
            }

            double x;
            double y;
            try {
                x = Double.parseDouble(matcher.group(1));
                y = Double.parseDouble(matcher.group(2));
            } catch (NumberFormatException ex) {
                return Optional.empty();
            }

            minX = minX == null ? x : Math.min(minX, x);
            maxX = maxX == null ? x : Math.max(maxX, x);
            minY = minY == null ? y : Math.min(minY, y);
            maxY = maxY == null ? y : Math.max(maxY, y);
        }

        Double height = parseDouble(machineProfile.path("printable_height"));
        if (minX == null || maxX == null || minY == null || maxY == null || height == null) {
            return Optional.empty();
        }

        double width = maxX - minX;
        double depth = maxY - minY;
        if (width <= 0 || depth <= 0 || height <= 0) {
            return Optional.empty();
        }

        return Optional.of(new PrintableVolume(width, depth, height));
    }

    static String buildOutOfVolumeMessage(String machineName,
                                          Optional<ModelDimensions> modelDimensions,
                                          Optional<PrintableVolume> printableVolume) {
        List<String> details = new ArrayList<>();
        modelDimensions.ifPresent(dimensions -> details.add(
                "model size " + formatMillimeters(dimensions.xMm())
                        + " x " + formatMillimeters(dimensions.yMm())
                        + " x " + formatMillimeters(dimensions.zMm()) + " mm"
        ));
        printableVolume.ifPresent(volume -> details.add(
                "printer limit " + formatMillimeters(volume.xMm())
                        + " x " + formatMillimeters(volume.yMm())
                        + " x " + formatMillimeters(volume.zMm()) + " mm"
        ));

        StringBuilder message = new StringBuilder("This model could not be placed fully inside the printer volume");
        if (!details.isEmpty()) {
            message.append(" (").append(String.join("; ", details)).append(")");
        }
        message.append(". Accept split printing to calculate it anyway, reduce the model size, or request a custom quote.");
        return message.toString();
    }

    private static Double extractDouble(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        try {
            return Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Double parseDouble(JsonNode valueNode) {
        if (valueNode == null || valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }

        String text = valueNode.asText(null);
        if (text == null || text.isBlank()) {
            return null;
        }

        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String formatMillimeters(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
