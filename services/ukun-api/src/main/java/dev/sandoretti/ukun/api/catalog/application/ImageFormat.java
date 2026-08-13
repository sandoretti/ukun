package dev.sandoretti.ukun.api.catalog.application;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The image formats a product photo may be in, recognised by the <strong>first bytes of the file</strong>
 * and never by its name or its declared {@code Content-Type} ({@code docs/08-identidad-y-seguridad.md}
 * §9).
 *
 * <p>That is the whole point of this enum. A {@code .jpg} whose contents are a script is not detected by
 * looking at the extension, and {@code Content-Type} is whatever the client chose to write in the part
 * header — both are claims made by the uploader. The magic number is the file itself.
 *
 * <p>It also decides the <em>extension of the stored key</em>, which closes the loop: the server names
 * the object after what the bytes turned out to be, so the type {@code MediaController} later derives
 * from that name cannot disagree with the content it serves.
 *
 * <p>It lives in {@code catalog} rather than in {@code storage} because "a product photo is a JPEG, a
 * PNG or a WebP" is a rule about the catalogue (RF-10). The port takes bytes and an extension; what is
 * acceptable is not its decision, and in v1.1 the S3 adapter will not have to learn it.
 */
enum ImageFormat {

    /** {@code FF D8 FF} — the SOI marker plus the first byte of the next one. */
    JPEG("jpg", "image/jpeg", 0, 0xFF, 0xD8, 0xFF),

    /** The eight-byte PNG signature, whose tail catches a file mangled by a text-mode transfer. */
    PNG("png", "image/png", 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),

    /**
     * {@code WEBP} at offset 8, inside a RIFF container. Checked here rather than at the start because
     * bytes 4 to 7 are the file length and vary — {@link #RIFF_HEADER} covers the {@code RIFF} half.
     */
    WEBP("webp", "image/webp", 8, 0x57, 0x45, 0x42, 0x50);

    /** The {@code RIFF} that must precede the {@code WEBP} tag; shared by every RIFF container. */
    private static final int[] RIFF_HEADER = {0x52, 0x49, 0x46, 0x46};

    /** What the {@code 415} reports as {@code allowed}, in the contract's own order. */
    static final List<String> ALLOWED_MEDIA_TYPES =
            Arrays.stream(values()).map(ImageFormat::mediaType).toList();

    private final String extension;
    private final String mediaType;
    private final int offset;
    private final int[] signature;

    ImageFormat(String extension, String mediaType, int offset, int... signature) {
        this.extension = extension;
        this.mediaType = mediaType;
        this.offset = offset;
        this.signature = signature;
    }

    String extension() {
        return extension;
    }

    String mediaType() {
        return mediaType;
    }

    /**
     * The format {@code content} really is, or empty when it is none of them — which the caller answers
     * with a {@code 415} rather than guessing.
     */
    static Optional<ImageFormat> detect(byte[] content) {
        return Arrays.stream(values()).filter(format -> format.matches(content)).findFirst();
    }

    private boolean matches(byte[] content) {
        if (this == WEBP && !startsWith(content, 0, RIFF_HEADER)) {
            return false;
        }
        return startsWith(content, offset, signature);
    }

    /**
     * Compared as unsigned, because a Java {@code byte} is signed and {@code 0x89} read out of a PNG is
     * {@code -119}. Getting that wrong makes the check reject every valid file of the format, which is
     * the kind of bug that looks like a broken upload rather than like a comparison.
     */
    private static boolean startsWith(byte[] content, int offset, int[] expected) {
        if (content.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((content[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
