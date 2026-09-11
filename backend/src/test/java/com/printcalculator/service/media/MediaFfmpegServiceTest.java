package com.printcalculator.service.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MediaFfmpegServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void sanitizeExecutable_rejectsControlCharacters() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> MediaFfmpegService.sanitizeExecutable("ffmpeg\n--help")
        );

        assertEquals("media.ffmpeg.path contains control characters.", ex.getMessage());
    }

    @Test
    void resolveExecutable_shouldFallbackToPathWhenAbsoluteLocationIsMissing() {
        String resolved = MediaFfmpegService.resolveExecutable(tempDir.resolve("ffmpeg").toString());

        assertEquals("ffmpeg", resolved);
    }

    @Test
    void generateVariant_rejectsSourceNamesStartingWithDash() throws Exception {
        MediaFfmpegService service = new MediaFfmpegService("missing-ffmpeg-binary");
        Path source = tempDir.resolve("-input.png");
        Path target = tempDir.resolve("output.jpg");
        Files.writeString(source, "image");

        IOException ex = assertThrows(
                IOException.class,
                () -> service.generateVariant(source, target, 120, 80, "JPEG")
        );

        assertEquals("Media source file name must not start with '-'.", ex.getMessage());
    }

    @Test
    void generateVariant_rejectsTargetNamesStartingWithDash() throws Exception {
        MediaFfmpegService service = new MediaFfmpegService("missing-ffmpeg-binary");
        Path source = tempDir.resolve("input.png");
        Path target = tempDir.resolve("-output.jpg");
        Files.writeString(source, "image");

        IOException ex = assertThrows(
                IOException.class,
                () -> service.generateVariant(source, target, 120, 80, "JPEG")
        );

        assertEquals("Media target file name must not start with '-'.", ex.getMessage());
    }

    @Test
    void generateVariant_avifShouldNotUseStillPictureFlag() throws Exception {
        Path fakeFfmpeg = tempDir.resolve("fake-ffmpeg.sh");
        Files.writeString(
                fakeFfmpeg,
                """
                        #!/bin/sh
                        if [ "$1" = "-hide_banner" ] && [ "$2" = "-encoders" ]; then
                          cat <<'EOF'
                         V..... mjpeg
                         V..... libwebp
                         V..... libaom-av1
                        EOF
                          exit 0
                        fi
                        if [ "$1" = "-hide_banner" ] && [ "$2" = "-muxers" ]; then
                          cat <<'EOF'
                         E avif
                        EOF
                          exit 0
                        fi

                        for arg in "$@"; do
                          if [ "$arg" = "-still-picture" ]; then
                            echo "Unrecognized option 'still-picture'. Error splitting the argument list: Option not found"
                            exit 1
                          fi
                        done

                        last_arg=""
                        for arg in "$@"; do
                          last_arg="$arg"
                        done

                        mkdir -p "$(dirname "$last_arg")"
                        printf 'ok' > "$last_arg"
                        exit 0
                        """
        );
        Files.setPosixFilePermissions(
                fakeFfmpeg,
                Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE
                )
        );

        MediaFfmpegService service = new MediaFfmpegService(fakeFfmpeg.toString());
        Path source = tempDir.resolve("input.png");
        Path target = tempDir.resolve("output.avif");
        Files.writeString(source, "image");

        service.generateVariant(source, target, 120, 80, "AVIF");

        assertTrue(Files.exists(target));
        assertEquals("ok", Files.readString(target));
    }

    @Test
    void canEncode_avifShouldRequireMuxerAvailability() throws Exception {
        Path fakeFfmpeg = tempDir.resolve("fake-ffmpeg-no-avif-muxer.sh");
        Files.writeString(
                fakeFfmpeg,
                """
                        #!/bin/sh
                        if [ "$1" = "-hide_banner" ] && [ "$2" = "-encoders" ]; then
                          cat <<'EOF'
                         V..... mjpeg
                         V..... libaom-av1
                        EOF
                          exit 0
                        fi
                        if [ "$1" = "-hide_banner" ] && [ "$2" = "-muxers" ]; then
                          cat <<'EOF'
                         E mp4
                        EOF
                          exit 0
                        fi
                        exit 0
                        """
        );
        Files.setPosixFilePermissions(
                fakeFfmpeg,
                Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE
                )
        );

        MediaFfmpegService service = new MediaFfmpegService(fakeFfmpeg.toString());

        assertTrue(service.canEncode("JPEG"));
        assertFalse(service.canEncode("AVIF"));
    }
}
