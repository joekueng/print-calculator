package com.printcalculator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.printcalculator.exception.ModelProcessingException;
import com.printcalculator.model.ModelDimensions;
import com.printcalculator.model.PrintStats;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Service
public class SlicerService {

    private static final Logger logger = Logger.getLogger(SlicerService.class.getName());

    private final String trustedSlicerPath;
    private final ProfileManager profileManager;
    private final GCodeParser gCodeParser;
    private final ObjectMapper mapper;
    private final SlicerModelInspector modelInspector;
    private final ThreeMfConversionSupport threeMfConversionSupport;

    public SlicerService(
            @Value("${slicer.path}") String slicerPath,
            @Value("${assimp.path:assimp}") String assimpPath,
            ProfileManager profileManager,
            GCodeParser gCodeParser,
            ObjectMapper mapper) {
        this.trustedSlicerPath = SlicerFileSupport.normalizeExecutablePath(slicerPath, "slicer.path");
        this.profileManager = profileManager;
        this.gCodeParser = gCodeParser;
        this.mapper = mapper;
        this.modelInspector = new SlicerModelInspector(trustedSlicerPath);
        this.threeMfConversionSupport = new ThreeMfConversionSupport(
                SlicerFileSupport.normalizeExecutablePath(assimpPath, "assimp.path")
        );
    }

    public PrintStats slice(File inputStl, String machineName, String filamentName, String processName,
                            Map<String, String> machineOverrides, Map<String, String> processOverrides) throws IOException {
        return sliceInternal(inputStl, machineName, filamentName, processName, machineOverrides, processOverrides, Optional.empty());
    }

    public PrintStats sliceForOversizedEstimate(File inputStl,
                                                String machineName,
                                                String filamentName,
                                                String processName,
                                                Optional<ModelDimensions> modelDimensions,
                                                Map<String, String> processOverrides) throws IOException {
        Optional<ModelDimensions> dimensions = modelDimensions != null ? modelDimensions : Optional.empty();
        return sliceInternal(inputStl, machineName, filamentName, processName, null, processOverrides, dimensions);
    }

    private PrintStats sliceInternal(File inputStl,
                                     String machineName,
                                     String filamentName,
                                     String processName,
                                     Map<String, String> machineOverrides,
                                     Map<String, String> processOverrides,
                                     Optional<ModelDimensions> oversizedEstimateDimensions) throws IOException {
        ObjectNode machineProfile = profileManager.getMergedProfile(machineName, "machine");
        ObjectNode filamentProfile = profileManager.getMergedProfile(filamentName, "filament");
        ObjectNode processProfile = profileManager.getMergedProfile(processName, "process");

        logger.info("Slicer profiles: machine='" + machineName + "', filament='" + filamentName + "', process='" + processName + "'");
        logger.info("Machine limits: printable_area=" + machineProfile.path("printable_area")
                + ", printable_height=" + machineProfile.path("printable_height")
                + ", bed_exclude_area=" + machineProfile.path("bed_exclude_area")
                + ", head_wrap_detect_zone=" + machineProfile.path("head_wrap_detect_zone"));

        if (machineOverrides != null) {
            machineOverrides.forEach(machineProfile::put);
        }
        if (processOverrides != null) {
            processOverrides.forEach(processProfile::put);
        }
        oversizedEstimateDimensions.ifPresent(dimensions -> applyOversizedEstimateVolume(machineProfile, dimensions));

        Path tempDir = Files.createTempDirectory("slicer_job_");
        try {
            File machineFile = tempDir.resolve("machine.json").toFile();
            File filamentFile = tempDir.resolve("filament.json").toFile();
            File processFile = tempDir.resolve("process.json").toFile();

            mapper.writeValue(machineFile, machineProfile);
            mapper.writeValue(filamentFile, filamentProfile);
            mapper.writeValue(processFile, processProfile);

            String basename = stripKnownModelExtension(inputStl.getName());
            Path slicerLogPath = tempDir.resolve("orcaslicer.log");
            String machineProfilePath = SlicerFileSupport.requireSafeArgument(
                    machineFile.getAbsolutePath(),
                    "machine profile path"
            );
            String processProfilePath = SlicerFileSupport.requireSafeArgument(
                    processFile.getAbsolutePath(),
                    "process profile path"
            );
            String filamentProfilePath = SlicerFileSupport.requireSafeArgument(
                    filamentFile.getAbsolutePath(),
                    "filament profile path"
            );
            String outputDirPath = SlicerFileSupport.requireSafeArgument(
                    tempDir.toAbsolutePath().toString(),
                    "output directory path"
            );
            String inputModelPath = SlicerFileSupport.requireSafeArgument(
                    inputStl.getAbsolutePath(),
                    "input model path"
            );

            List<SlicerInputVariant> slicerInputVariants = resolveSlicerInputVariants(inputStl, inputModelPath, tempDir);
            List<SliceAttempt> sliceAttempts = buildSliceAttempts();
            ModelProcessingException lastFailure = null;

            for (int variantIndex = 0; variantIndex < slicerInputVariants.size(); variantIndex++) {
                SlicerInputVariant inputVariant = slicerInputVariants.get(variantIndex);
                boolean hasMoreInputVariants = variantIndex < slicerInputVariants.size() - 1;

                for (int attemptIndex = 0; attemptIndex < sliceAttempts.size(); attemptIndex++) {
                    SliceAttempt attempt = sliceAttempts.get(attemptIndex);
                    ProcessBuilder processBuilder = buildSliceProcess(
                            attempt,
                            machineProfilePath,
                            processProfilePath,
                            filamentProfilePath,
                            outputDirPath,
                            inputVariant
                    );

                    logger.info("Executing Slicer" + attempt.logSuffix() + " using " + inputVariant.label()
                            + ": " + String.join(" ", processBuilder.command()));

                    Files.deleteIfExists(slicerLogPath);
                    processBuilder.directory(tempDir.toFile());
                    processBuilder.redirectErrorStream(true);
                    processBuilder.redirectOutput(slicerLogPath.toFile());

                    Process process = processBuilder.start();
                    boolean finished = process.waitFor(5, TimeUnit.MINUTES);
                    if (!finished) {
                        process.destroyForcibly();
                        throw new ModelProcessingException(
                                "SLICER_TIMEOUT",
                                "Model processing timed out. Try another format or contact us directly via Request Consultation."
                        );
                    }

                    String output = Files.exists(slicerLogPath)
                            ? Files.readString(slicerLogPath, StandardCharsets.UTF_8)
                            : "";

                    if (process.exitValue() != 0) {
                        boolean hasMorePlacementAttempts = attemptIndex < sliceAttempts.size() - 1;
                        if (hasMorePlacementAttempts && isOutOfVolumeError(output)) {
                            logger.warning("Slicer reported model out of printable area for " + inputVariant.label()
                                    + ", retrying" + sliceAttempts.get(attemptIndex + 1).logSuffix() + ".");
                            continue;
                        }

                        lastFailure = isOutOfVolumeError(output)
                                ? buildOutOfVolumeFailure(inputStl, machineName, machineProfile)
                                : new ModelProcessingException(
                                "SLICER_EXECUTION_FAILED",
                                "Unable to process this model. Try another format or contact us directly via Request Consultation."
                        );

                        if (hasMoreInputVariants) {
                            logger.warning("Slicer failed with exit code " + process.exitValue() + " for "
                                    + inputVariant.label() + ". Retrying with fallback geometry. Log: " + output);
                            break;
                        }

                        logger.warning("Slicer failed with exit code " + process.exitValue() + ". Log: " + output);
                        throw lastFailure;
                    }

                    File gcodeFile = resolveGcodeFile(tempDir, basename);
                    if (gcodeFile == null) {
                        lastFailure = new ModelProcessingException(
                                "SLICER_OUTPUT_MISSING",
                                "Unable to generate slicing output for this model. Try another format or contact us directly via Request Consultation."
                        );
                        if (hasMoreInputVariants) {
                            logger.warning("Slicer succeeded but no G-code was generated for " + inputVariant.label()
                                    + ". Retrying with fallback geometry.");
                            break;
                        }
                        throw lastFailure;
                    }

                    return gCodeParser.parse(gcodeFile);
                }
            }

            if (lastFailure != null) {
                throw lastFailure;
            }

            throw new ModelProcessingException(
                    "SLICER_FAILED_AFTER_RETRY",
                    "Unable to process this model. Try another format or contact us directly via Request Consultation."
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during slicing", e);
        } finally {
            SlicerFileSupport.deleteRecursively(tempDir, logger);
        }
    }

    private void applyOversizedEstimateVolume(ObjectNode machineProfile, ModelDimensions dimensions) {
        double marginMm = 50.0;
        double sideMm = Math.max(dimensions.xMm(), dimensions.yMm()) + marginMm;
        double heightMm = dimensions.zMm() + marginMm;

        ArrayNode printableArea = mapper.createArrayNode();
        printableArea.add("0x0");
        printableArea.add(formatProfileMillimeters(sideMm) + "x0");
        printableArea.add(formatProfileMillimeters(sideMm) + "x" + formatProfileMillimeters(sideMm));
        printableArea.add("0x" + formatProfileMillimeters(sideMm));

        machineProfile.set("printable_area", printableArea);
        machineProfile.put("printable_height", formatProfileMillimeters(heightMm));
        machineProfile.putArray("bed_exclude_area");
        machineProfile.putArray("head_wrap_detect_zone");

        logger.info("Using virtual build volume for oversized estimate: "
                + formatProfileMillimeters(sideMm) + " x "
                + formatProfileMillimeters(sideMm) + " x "
                + formatProfileMillimeters(heightMm) + " mm");
    }

    public Optional<ModelDimensions> inspectModelDimensions(File inputModel) {
        return modelInspector.inspectModelDimensions(inputModel);
    }

    static Optional<ModelDimensions> parseModelDimensionsFromInfoOutput(String output) {
        return SlicerModelInspector.parseModelDimensionsFromInfoOutput(output);
    }

    static Optional<PrintableVolume> extractPrintableVolume(JsonNode machineProfile) {
        return SlicerModelInspector.extractPrintableVolume(machineProfile);
    }

    static String buildOutOfVolumeMessage(String machineName,
                                          Optional<ModelDimensions> modelDimensions,
                                          Optional<PrintableVolume> printableVolume) {
        return SlicerModelInspector.buildOutOfVolumeMessage(machineName, modelDimensions, printableVolume);
    }

    public Path convert3mfToPersistentStl(File input3mf, Path destinationStl) throws IOException {
        return threeMfConversionSupport.convert3mfToPersistentStl(input3mf, destinationStl);
    }

    private ProcessBuilder buildSliceProcess(SliceAttempt attempt,
                                             String machineProfilePath,
                                             String processProfilePath,
                                             String filamentProfilePath,
                                             String outputDirPath,
                                             SlicerInputVariant inputVariant) {
        ProcessBuilder processBuilder = new ProcessBuilder();
        List<String> command = processBuilder.command();
        command.add(trustedSlicerPath);
        command.add("--load-settings");
        command.add(machineProfilePath);
        command.add("--load-settings");
        command.add(processProfilePath);
        command.add("--load-filaments");
        command.add(filamentProfilePath);
        command.add("--ensure-on-bed");
        if (attempt.arrange()) {
            command.add("--arrange");
            command.add("1");
        }
        if (attempt.allowRotations()) {
            command.add("--allow-rotations");
        }
        if (attempt.orient()) {
            command.add("--orient");
            command.add("1");
        }
        command.add("--slice");
        command.add("0");
        command.add("--outputdir");
        command.add(outputDirPath);
        command.addAll(inputVariant.inputPaths());
        return processBuilder;
    }

    private File resolveGcodeFile(Path tempDir, String basename) {
        File gcodeFile = tempDir.resolve(basename + ".gcode").toFile();
        if (gcodeFile.exists()) {
            return gcodeFile;
        }

        File fallback = tempDir.resolve("plate_1.gcode").toFile();
        return fallback.exists() ? fallback : null;
    }

    private String formatProfileMillimeters(double value) {
        return BigDecimal.valueOf(Math.ceil(value)).stripTrailingZeros().toPlainString();
    }

    private boolean isOutOfVolumeError(String errorLog) {
        if (errorLog == null || errorLog.isBlank()) {
            return false;
        }

        String normalized = errorLog.toLowerCase();
        return normalized.contains("nothing to be sliced")
                || normalized.contains("no object is fully inside the print volume")
                || normalized.contains("calc_exclude_triangles");
    }

    static List<SliceAttempt> buildSliceAttempts() {
        return List.of(
                new SliceAttempt(false, false, false),
                new SliceAttempt(true, false, false),
                new SliceAttempt(true, true, false),
                new SliceAttempt(true, true, true)
        );
    }

    static String stripKnownModelExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "model";
        }

        String lower = filename.toLowerCase();
        for (String extension : List.of(".stl", ".3mf", ".obj", ".step", ".stp")) {
            if (lower.endsWith(extension)) {
                return filename.substring(0, filename.length() - extension.length());
            }
        }
        return filename;
    }

    private List<SlicerInputVariant> resolveSlicerInputVariants(File inputModel, String inputModelPath, Path tempDir)
            throws IOException, InterruptedException {
        if (!inputModel.getName().toLowerCase().endsWith(".3mf")) {
            return List.of(new SlicerInputVariant(List.of(inputModelPath), "original model"));
        }

        List<SlicerInputVariant> variants = new ArrayList<>();
        variants.add(new SlicerInputVariant(List.of(inputModelPath), "original 3MF project"));

        try {
            List<String> convertedStlPaths = threeMfConversionSupport.convert3mfToStlInputPaths(inputModel, tempDir);
            logger.info("Prepared converted 3MF fallback with " + convertedStlPaths.size() + " STL file(s) for slicing.");
            variants.add(new SlicerInputVariant(convertedStlPaths, "converted 3MF geometry"));
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            logger.warning("Failed to prepare converted 3MF fallback for " + inputModel.getName() + ": " + e.getMessage());
        }

        return variants;
    }

    private ModelProcessingException buildOutOfVolumeFailure(File inputModel,
                                                             String machineName,
                                                             ObjectNode machineProfile) {
        return new ModelProcessingException(
                "MODEL_OUT_OF_PRINT_VOLUME",
                buildOutOfVolumeMessage(
                        machineName,
                        inspectModelDimensions(inputModel),
                        extractPrintableVolume(machineProfile)
                )
        );
    }

    record SliceAttempt(boolean arrange, boolean allowRotations, boolean orient) {
        String logSuffix() {
            if (!arrange && !allowRotations && !orient) {
                return "";
            }

            List<String> flags = new ArrayList<>();
            if (arrange) {
                flags.add("arrange");
            }
            if (allowRotations) {
                flags.add("allow-rotations");
            }
            if (orient) {
                flags.add("orient");
            }
            return " (retry with " + String.join(" + ", flags) + ")";
        }
    }

    private record SlicerInputVariant(List<String> inputPaths, String label) {
    }
}
