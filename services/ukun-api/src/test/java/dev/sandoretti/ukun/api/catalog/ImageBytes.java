package dev.sandoretti.ukun.api.catalog;

/**
 * Files that begin like an image and files that do not — the only thing the upload of RF-10 judges an
 * upload by ({@code docs/08-identidad-y-seguridad.md} §9).
 *
 * <p>It sits next to {@code ProductMother} and is used the same way: two suites need the same bytes for
 * different reasons — {@code ImageFormatTest} to check the detection in isolation, {@code ProductImageIT}
 * to send them through the endpoint — and a second copy of the signatures would eventually disagree
 * with this one about what a WebP looks like. The signature <em>is</em> the fixture here.
 *
 * <p>The content after the header is zeroes: nothing in this system decodes an image, so a real photo
 * would only make the test slower and its intent less clear.
 */
public final class ImageBytes {

    private ImageBytes() {}

    /** The eight-byte PNG signature. */
    public static byte[] png(int size) {
        return headedBy(size,0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
    }

    /** {@code FF D8 FF} — a JPEG's SOI marker. */
    public static byte[] jpeg(int size) {
        return headedBy(size,0xFF, 0xD8, 0xFF);
    }

    /** A RIFF container whose tag at offset 8 is {@code WEBP}; bytes 4 to 7 are the length and vary. */
    public static byte[] webp(int size) {
        return headedBy(size,0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50);
    }

    /** A file of exactly these bytes, for the cases about what must <em>not</em> be recognised. */
    public static byte[] of(int... bytes) {
        return headedBy(bytes.length, bytes);
    }

    /** {@code size} bytes, the first of which are {@code signature} and the rest zeroes. */
    private static byte[] headedBy(int size, int... signature) {
        byte[] content = new byte[Math.max(size, signature.length)];
        for (int i = 0; i < signature.length; i++) {
            content[i] = (byte) signature[i];
        }

        return content;
    }
}
