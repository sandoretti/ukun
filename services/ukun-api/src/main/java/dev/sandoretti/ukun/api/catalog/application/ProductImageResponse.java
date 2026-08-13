package dev.sandoretti.ukun.api.catalog.application;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The result of an image upload — the {@code ProductImage} schema of the contract (RF-10).
 *
 * <p>One field, and the field is the <strong>servable URL, never {@code product.image_key}</strong>.
 * The storage key is an internal detail of {@code FileStoragePort} and changes shape in v1.1, when the
 * filesystem adapter becomes object storage; a client that had been handed it would break then, and one
 * that composes URLs out of identifiers is the habit {@code docs/08-identidad-y-seguridad.md} §9 sets
 * out to prevent.
 *
 * <p>Not {@code AdminProduct}: the image is a sub-resource with its own URL, and answering with the
 * whole product would make the upload look like an edit of it.
 */
public record ProductImageResponse(@Schema(format = "uri-reference") String imageUrl) {}
