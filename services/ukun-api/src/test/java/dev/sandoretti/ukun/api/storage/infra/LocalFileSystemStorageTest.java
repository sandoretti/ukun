package dev.sandoretti.ukun.api.storage.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sandoretti.ukun.api.storage.application.StorageProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.Resource;

/**
 * The barrier of {@code docs/08-identidad-y-seguridad.md} §9: this route serves nothing the adapter
 * did not write. The key arrives from the URL, so most of this class is about the keys that must not
 * resolve to anything — a traversal is not a corner case here, it is the threat.
 *
 * <p>No Spring context and no container: a temporary directory and plain calls. The root is a
 * <em>subdirectory</em> of the temporary directory so that "outside the root" is somewhere the test
 * can legitimately write to.
 */
class LocalFileSystemStorageTest {

    private static final byte[] IMAGE_BYTES = "not really a jpeg, but bytes are bytes".getBytes(StandardCharsets.UTF_8);

    @TempDir
    private Path base;

    private Path root;

    private LocalFileSystemStorage storage;

    @BeforeEach
    void createTheAdapterOverATemporaryRoot() throws IOException {
        root = Files.createDirectory(base.resolve("uploads"));
        Files.write(root.resolve("mesa-madera.jpg"), IMAGE_BYTES);

        storage = new LocalFileSystemStorage(propertiesFor(root));
    }

    @Test
    @DisplayName("a key that names a file under the root resolves to its contents")
    void shouldLoadAFileThatExists() throws IOException {
        Optional<Resource> loaded = storage.load("mesa-madera.jpg");

        assertThat(loaded).isPresent();
        assertThat(loaded.get().getContentAsByteArray()).isEqualTo(IMAGE_BYTES);
    }

    @Test
    @DisplayName("a key that names no file resolves to nothing")
    void shouldNotLoadAMissingFile() {
        assertThat(storage.load("no-existe.jpg")).isEmpty();
    }

    /**
     * Every one of these has to come back empty and, crucially, <em>indistinguishable</em> from the
     * missing-file case above: a probe and a typo must look the same from outside.
     *
     * <p>{@code ../../etc/passwd} appears both raw and percent-encoded because the port cannot assume
     * which form reaches it — Spring decodes the path variable before the method runs, so the adapter
     * has to reject the decoded string rather than trust that the encoding was refused upstream.
     */
    @ParameterizedTest(name = "[{index}] {0}")
    @NullSource
    @ValueSource(
            strings = {
                "",
                "..",
                "../../etc/passwd",
                "..%2f..%2fetc%2fpasswd",
                "/etc/passwd",
                "subdir/mesa-madera.jpg",
                "..\\..\\windows\\system32",
                ".hidden",
                "mesa madera.jpg"
            })
    @DisplayName("a key that is not a plain single segment resolves to nothing")
    void shouldRejectKeysThatCouldLeaveTheRoot(String key) {
        assertThat(storage.load(key)).isEmpty();
    }

    @Test
    @DisplayName("no key can reach a file that sits outside the root")
    void shouldNotReachASiblingOfTheRoot() throws IOException {
        Files.write(base.resolve("secret.txt"), "no".getBytes(StandardCharsets.UTF_8));

        assertThat(storage.load("../secret.txt")).isEmpty();
    }

    /**
     * A symlink is the case {@code normalize()} cannot see: the key is a well-formed single segment and
     * the resolved path starts with the root, yet the bytes live somewhere else. It is why the adapter
     * checks {@code toRealPath()} as well.
     */
    @Test
    @DisplayName("a symlink pointing out of the root resolves to nothing")
    void shouldNotFollowASymlinkOutOfTheRoot() throws IOException {
        Path outside = base.resolve("outside.jpg");
        Files.write(outside, IMAGE_BYTES);
        Files.createSymbolicLink(root.resolve("escape.jpg"), outside);

        assertThat(storage.load("escape.jpg")).isEmpty();
    }

    @Test
    @DisplayName("a directory is not a file, however well its name is formed")
    void shouldNotLoadADirectory() throws IOException {
        Files.createDirectory(root.resolve("thumbnails"));

        assertThat(storage.load("thumbnails")).isEmpty();
    }

    /**
     * A missing bind mount is a deployment fault. It answers "no file" instead of throwing, because a
     * broken {@code <img>} is the honest rendering of a missing image and a 500 per photo is not.
     */
    @Test
    @DisplayName("an unmounted storage root answers as if nothing were stored")
    void shouldNotFailWhenTheRootDoesNotExist() {
        LocalFileSystemStorage unmounted = new LocalFileSystemStorage(propertiesFor(base.resolve("never-mounted")));

        assertThat(unmounted.load("mesa-madera.jpg")).isEmpty();
    }

    /**
     * The write half (RF-10). The key is the adapter's to invent — a UUID plus the extension the caller
     * derived from the file's real content — so nothing the client sent survives into the name and there
     * is no traversal left to sanitise.
     */
    @Test
    @DisplayName("a stored file gets a generated key, never the caller's name")
    void shouldStoreUnderAGeneratedKey() throws IOException {
        String key = storage.store(IMAGE_BYTES, "png");

        assertThat(key)
                .endsWith(".png")
                .doesNotContain("/")
                .hasSize("00000000-0000-0000-0000-000000000000.png".length());
        assertThat(Files.readAllBytes(root.resolve(key))).isEqualTo(IMAGE_BYTES);
    }

    /**
     * Two uploads never collide and never overwrite: the second one is a new object, which is what lets
     * the use case replace an image by writing first and deleting afterwards.
     */
    @Test
    @DisplayName("storing twice writes two objects")
    void shouldNeverOverwrite() {
        String first = storage.store(IMAGE_BYTES, "png");
        String second = storage.store(IMAGE_BYTES, "png");

        assertThat(first).isNotEqualTo(second);
        assertThat(root.resolve(first)).exists();
        assertThat(root.resolve(second)).exists();
    }

    /** The directory of the bind mount may not be there yet on a fresh checkout. */
    @Test
    @DisplayName("storing creates the root if it is missing")
    void shouldCreateTheRootOnDemand() {
        Path missing = base.resolve("not-yet");
        LocalFileSystemStorage fresh = new LocalFileSystemStorage(propertiesFor(missing));

        String key = fresh.store(IMAGE_BYTES, "webp");

        assertThat(missing.resolve(key)).exists();
    }

    /**
     * A failed write is loud, unlike a failed read: the caller is about to point {@code product.image_key}
     * at this key, and a row pointing at bytes that were never written is worse than a failed upload.
     */
    @Test
    @DisplayName("a root that cannot be created fails the call instead of pretending")
    void shouldFailWhenItCannotWrite() throws IOException {
        Path file = base.resolve("a-file-not-a-directory");
        Files.write(file, IMAGE_BYTES);
        LocalFileSystemStorage broken = new LocalFileSystemStorage(propertiesFor(file));

        assertThatThrownBy(() -> broken.store(IMAGE_BYTES, "png")).isInstanceOf(UncheckedIOException.class);
    }

    @Test
    @DisplayName("deleting removes the object and says so")
    void shouldDeleteAStoredFile() {
        assertThat(storage.delete("mesa-madera.jpg")).isTrue();
        assertThat(root.resolve("mesa-madera.jpg")).doesNotExist();
    }

    /**
     * Both "there was nothing" and "it could not be removed" answer {@code false}, because the caller
     * treats them the same: deleting the old object is compensation for a write that already succeeded.
     */
    @Test
    @DisplayName("deleting what is not there is not a failure")
    void shouldNotFailDeletingAMissingFile() {
        assertThat(storage.delete("no-existe.jpg")).isFalse();
    }

    /**
     * The key travelled through the database, but it is still validated: a deletion that could name a
     * parent directory would be a worse version of the traversal the read half refuses.
     */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"../outside.jpg", "sub/dir.jpg", ".hidden"})
    @DisplayName("a malformed key deletes nothing")
    void shouldNotDeleteThroughAMalformedKey(String key) throws IOException {
        Path outside = base.resolve("outside.jpg");
        Files.write(outside, IMAGE_BYTES);

        assertThat(storage.delete(key)).isFalse();
        assertThat(outside).exists();
    }

    private static StorageProperties propertiesFor(Path localRoot) {
        StorageProperties properties = new StorageProperties();
        properties.setLocalRoot(localRoot);

        return properties;
    }
}
