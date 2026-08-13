package dev.sandoretti.ukun.api.catalog.application;

import dev.sandoretti.ukun.api.shared.error.ErrorCode;
import dev.sandoretti.ukun.api.shared.error.UkunException;
import java.util.Map;

/**
 * The uploaded file is not one of the formats a product photo may be in (RF-10,
 * {@code docs/08-identidad-y-seguridad.md} §9).
 *
 * <p>It reports {@code allowed} and says nothing about what the file actually was: naming the detected
 * type would be echoing the uploader's own bytes back at them, and the frontend only needs the list to
 * paint "JPEG, PNG or WebP".
 *
 * <p>A {@code 415} raised by the domain and not by content negotiation. Spring's own
 * {@code HttpMediaTypeNotSupportedException} answers the same status when the request's
 * {@code Content-Type} is wrong, which is a different fact: that one is about the header, this one is
 * about the file. Both come back as a {@code ProblemDetail}, but only this one can tell that a
 * {@code .jpg} was never an image.
 */
final class UnsupportedImageTypeException extends UkunException {

    UnsupportedImageTypeException() {
        super("The uploaded file is not an accepted image format");
    }

    @Override
    public ErrorCode getCode() {
        return ErrorCode.UNSUPPORTED_MEDIA_TYPE;
    }

    @Override
    public Map<String, Object> getExtensions() {
        return Map.of("allowed", ImageFormat.ALLOWED_MEDIA_TYPES);
    }
}
