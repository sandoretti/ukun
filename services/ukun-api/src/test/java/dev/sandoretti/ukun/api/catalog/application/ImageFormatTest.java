package dev.sandoretti.ukun.api.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sandoretti.ukun.api.catalog.ImageBytes;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The magic-number check of {@code docs/08-identidad-y-seguridad.md} §9. What it has to get right is
 * not "recognises a PNG" — it is "does not recognise anything else", because the input is a file a
 * stranger chose to upload.
 */
class ImageFormatTest {

    @Test
    @DisplayName("the three accepted formats are recognised by their first bytes")
    void detectsTheAllowedFormats() {
        assertThat(ImageFormat.detect(ImageBytes.of(0xFF, 0xD8, 0xFF, 0xE0))).contains(ImageFormat.JPEG);
        assertThat(ImageFormat.detect(ImageBytes.png(8))).contains(ImageFormat.PNG);
        assertThat(ImageFormat.detect(ImageBytes.webp(12))).contains(ImageFormat.WEBP);
    }

    /**
     * {@code 0x89} is {@code -119} as a Java {@code byte}. Comparing it signed makes the check reject
     * every valid PNG there is, which looks like a broken upload rather than like a comparison — hence a
     * case of its own.
     */
    @Test
    @DisplayName("a signature byte above 0x7F is compared unsigned")
    void comparesBytesUnsigned() {
        assertThat(ImageFormat.detect(ImageBytes.png(8))).isPresent();
    }

    /** The whole point: a script named {@code .png} is not a PNG, and only the content says so. */
    @Test
    @DisplayName("a file that is not an image is not detected as one")
    void rejectsAnythingElse() {
        assertThat(ImageFormat.detect("#!/bin/sh\n".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(ImageFormat.detect("GIF89a".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(ImageFormat.detect(new byte[0])).isEmpty();
    }

    /**
     * A RIFF container that is not a WebP — an AVI, say. The {@code WEBP} tag sits at offset 8, after
     * four bytes of file length, so checking only the {@code RIFF} header would accept it.
     */
    @Test
    @DisplayName("a RIFF file that is not a WebP is rejected")
    void rejectsARiffThatIsNotWebp() {
        byte[] avi = ImageBytes.webp(12);
        avi[8] = 'A';
        avi[9] = 'V';
        avi[10] = 'I';
        avi[11] = ' ';

        assertThat(ImageFormat.detect(avi)).isEmpty();
    }

    /** A file shorter than the signature it would need cannot match it, and must not read past its end. */
    @Test
    @DisplayName("a truncated file is rejected rather than read past its end")
    void rejectsATruncatedFile() {
        Optional<ImageFormat> detected = ImageFormat.detect(ImageBytes.of(0x89, 0x50));

        assertThat(detected).isEmpty();
    }

    /** The extension of the stored key comes from here, never from the uploaded filename. */
    @Test
    @DisplayName("each format names the extension its stored key will carry")
    void namesItsOwnExtension() {
        assertThat(ImageFormat.JPEG.extension()).isEqualTo("jpg");
        assertThat(ImageFormat.PNG.extension()).isEqualTo("png");
        assertThat(ImageFormat.WEBP.extension()).isEqualTo("webp");
        assertThat(ImageFormat.ALLOWED_MEDIA_TYPES).containsExactly("image/jpeg", "image/png", "image/webp");
    }
}
