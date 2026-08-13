package dev.sandoretti.ukun.api.storage;

import java.util.Optional;
import org.springframework.core.io.Resource;

/**
 * The public API of the {@code storage} module (ADR-0017 §1 and §5): the boundary between this
 * service and wherever binary files actually live. Today a directory on disk, object storage in v1.1
 * — and the point of the port is that the change is one adapter and nothing else.
 *
 * <p>The read half arrived first, in v0.3: {@code catalog} already handed the browser an
 * {@code imageUrl} and nothing was serving it. The write half is v0.7 (RF-10), and it is deliberately
 * two methods rather than one {@code replace(oldKey, newKey)}: the ordering between them is the
 * compensation {@code docs/08-identidad-y-seguridad.md} §9 requires, and it belongs to the use case
 * that also owns the database row — the legacy loses the old photo precisely because storage decided
 * that ordering on its own.
 *
 * <p>A key is opaque to every caller. {@code product.image_key} never leaves the service, and the URL
 * the browser gets is composed by {@code catalog} from the configured public prefix.
 *
 * <p><strong>What this port does not decide is which files are acceptable.</strong> The allowed formats
 * of RF-10 are a catalogue rule, checked before anything reaches here; storage takes bytes and an
 * extension and answers where they went. That split is what keeps the S3 adapter of v1.1 from having
 * to know what a product photo is.
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

    /**
     * Writes {@code content} under a <strong>freshly generated key</strong> and returns it.
     *
     * <p>The key is the adapter's to invent, never the caller's and above all never the client's: it is
     * a UUID plus {@code extension}, which closes path traversal through a crafted filename without
     * anyone having to sanitise one ({@code docs/08-identidad-y-seguridad.md} §9). It also means this
     * method never overwrites: a replacement is a new object plus a deletion of the old one, in that
     * order, decided by the caller.
     *
     * @param extension the file extension, without a dot, derived by the caller from the detected
     *     content of the file — not from the name the client sent
     * @throws java.io.UncheckedIOException if the object could not be written. Unlike {@link
     *     #load(String)}, a failure here is not something to answer with "no file": the caller is about
     *     to point a database row at this key, and it must not do so if the bytes are not there.
     */
    String store(byte[] content, String extension);

    /**
     * Removes the object a key names, if it is there.
     *
     * @return {@code true} when this call removed it. {@code false} covers both "there was nothing" and
     *     "it could not be removed", which the caller treats the same way: the deletion of an old image
     *     is compensation for a write that already succeeded, so it is worth logging and never worth
     *     failing the request over. An orphaned file costs disk; a lost one costs the photo.
     */
    boolean delete(String key);
}
