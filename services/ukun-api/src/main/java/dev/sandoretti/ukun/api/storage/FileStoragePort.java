package dev.sandoretti.ukun.api.storage;

import java.util.Optional;
import org.springframework.core.io.Resource;

/**
 * The public API of the {@code storage} module (ADR-0017 §1 and §5): the boundary between this
 * service and wherever binary files actually live. Today a directory on disk, object storage in v1.1
 * — and the point of the port is that the change is one adapter and nothing else.
 *
 * <p><strong>Only the read half exists.</strong> Writing (the multipart {@code PUT
 * /api/admin/products/{id}/image} of RF-10, its 5 MB limit and its magic-number check) belongs to
 * v0.7 and is deliberately absent: this module was brought forward because {@code catalog} already
 * hands the browser an {@code imageUrl} in v0.3 and nothing was serving it. Adding {@code store(...)}
 * here without the compensation logic {@code docs/08-identidad-y-seguridad.md} §9 requires would be
 * an invitation to use it.
 *
 * <p>A key is opaque to every caller. {@code product.image_key} never leaves the service, and the URL
 * the browser gets is composed by {@code catalog} from the configured public prefix.
 */
public interface FileStoragePort {

    /**
     * Resolves a storage key to something readable, or {@link Optional#empty()} when it names no file
     * this adapter can serve.
     *
     * <p>Empty is the answer for a key that does not exist <em>and</em> for a key that tries to leave
     * the storage root. Callers get no way to tell the two apart, which is the intended behaviour: a
     * probe for {@code ../../etc/passwd} and a typo must look identical from outside.
     */
    Optional<Resource> load(String key);
}
