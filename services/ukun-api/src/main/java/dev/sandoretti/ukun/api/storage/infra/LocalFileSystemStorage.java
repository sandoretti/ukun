package dev.sandoretti.ukun.api.storage.infra;

import dev.sandoretti.ukun.api.storage.FileStoragePort;
import dev.sandoretti.ukun.api.storage.application.StorageProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * The filesystem adapter of {@link FileStoragePort}: reads from the directory bind-mounted at
 * {@code ukun.storage.local-root}. Replaced by an S3/MinIO adapter in v1.1 (ADR-0017 §1), which is
 * the whole reason the port exists.
 *
 * <p><strong>The key arrives from the URL, so this class is the barrier.</strong> Serving a file
 * whose name a stranger chose is how {@code /media/products/../../etc/passwd} becomes an arbitrary
 * file read, and {@code docs/08-identidad-y-seguridad.md} §9 states the property to hold: the route
 * serves nothing the adapter did not write. It is enforced twice, on purpose:
 *
 * <ol>
 *   <li>the key must match {@link #SAFE_KEY} — one path segment, no separator, no leading dot — which
 *       rejects the traversal before any syscall happens;
 *   <li>the resolved file must still be under the real storage root, which also covers a symlink
 *       inside the directory pointing somewhere else.
 * </ol>
 *
 * <p>The second check is not redundant with the first. The first can be loosened one day (a future
 * layout with {@code products/2026/} prefixes would need it); the second holds whatever the key looks
 * like, and is the one that states the actual invariant.
 */
@Component
class LocalFileSystemStorage implements FileStoragePort {

    private static final Logger log = LoggerFactory.getLogger(LocalFileSystemStorage.class);

    /**
     * A single path segment: starts alphanumeric, then alphanumerics, dot, dash or underscore. It
     * admits both shapes of key this repo has — the readable {@code mesa-madera.jpg} of the demo seed
     * and the server-generated {@code <uuid>.webp} of RF-10 — and admits no separator, so {@code ..}
     * cannot be a segment and no key can name a parent directory.
     */
    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,254}");

    private final StorageProperties properties;

    LocalFileSystemStorage(StorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<Resource> load(String key) {
        return under(key)
                .filter(Files::isRegularFile)
                .filter(Files::isReadable)
                .flatMap(LocalFileSystemStorage::servable);
    }

    /**
     * A UUID plus the extension the caller derived from the file's real content. Nothing of what the
     * client sent survives into the name, so there is no traversal to sanitise and no collision to
     * resolve — and {@code CREATE_NEW} turns the impossible one into a failure rather than into a
     * silent overwrite of somebody else's photo.
     *
     * <p>The write is not atomic against a reader mid-read, and does not need to be: the key is new, so
     * nobody can be reading it yet. That is the property {@link #store} buys by never overwriting, and
     * it is what makes the temp-file-and-rename dance unnecessary here.
     */
    @Override
    public String store(byte[] content, String extension) {
        String key = UUID.randomUUID() + "." + extension;

        try {
            Path root = properties.getLocalRoot();
            Files.createDirectories(root);
            Files.write(root.resolve(key), content, StandardOpenOption.CREATE_NEW);

            return key;

        } catch (IOException e) {
            // Loud, unlike a failed read: the caller is about to point product.image_key at this key,
            // and a row pointing at bytes that were never written is worse than a failed upload.
            throw new UncheckedIOException("Could not store the file under " + properties.getLocalRoot(), e);
        }
    }

    /**
     * Validated by the same {@link #under(String)} the read goes through, which is the point of that
     * method existing: the caller here is this service rather than a browser, but the value travelled
     * through the database, and a deletion that could name a parent directory would be a worse version
     * of the same hole. Two barriers written twice would be one barrier and one that drifted.
     */
    @Override
    public boolean delete(String key) {
        return under(key)
                .map(file -> {
                    try {
                        return Files.deleteIfExists(file);
                    } catch (IOException e) {
                        // Never fatal: this is always compensation for a write that already succeeded,
                        // so the request it belongs to is finished either way. What is left behind is an
                        // orphaned file.
                        log.warn("Could not delete the stored file", e);
                        return false;
                    }
                })
                .orElse(false);
    }

    /**
     * The file a key names, or empty when the key is not a plain single segment, when the resolved path
     * would leave the root, or when the root itself is not there.
     *
     * <p>It is the barrier of {@code docs/08-identidad-y-seguridad.md} §9 in one place, called by both
     * halves of the port. Empty is the same answer for a probe and for a typo, which is deliberate: from
     * outside, the two must be indistinguishable.
     */
    private Optional<Path> under(String key) {
        if (key == null || !SAFE_KEY.matcher(key).matches()) {
            // At debug: a rejected key is noise under a scanner, and the key is attacker-controlled
            // text that has no business at a level anyone watches.
            log.debug("Rejected a malformed storage key");
            return Optional.empty();
        }

        return realRoot().flatMap(root -> {
            Path file = root.resolve(key).normalize();
            return file.startsWith(root) ? Optional.of(file) : Optional.empty();
        });
    }

    /**
     * An unreadable or missing root is a deployment fault (a forgotten bind mount), not a request fault.
     * It is logged and answered as "no file" rather than as a 500: a broken {@code <img>} is the honest
     * rendering of a missing image, and the mount is what needs fixing.
     */
    private Optional<Path> realRoot() {
        try {
            return Optional.of(properties.getLocalRoot().toRealPath());
        } catch (IOException | InvalidPathException e) {
            log.warn("Storage root {} is not readable", properties.getLocalRoot(), e);
            return Optional.empty();
        }
    }

    /**
     * The last check, and the one {@link #under(String)} cannot make: a symlink whose name is a
     * well-formed single segment and whose resolved path starts with the root, but whose bytes live
     * somewhere else. It runs after the existence check, so {@code toRealPath} cannot throw for a file
     * that is simply absent.
     */
    private static Optional<Resource> servable(Path file) {
        try {
            return file.toRealPath().startsWith(file.getParent().toRealPath())
                    ? Optional.of(new FileSystemResource(file))
                    : Optional.empty();

        } catch (IOException e) {
            log.warn("Could not resolve a stored file", e);
            return Optional.empty();
        }
    }
}
