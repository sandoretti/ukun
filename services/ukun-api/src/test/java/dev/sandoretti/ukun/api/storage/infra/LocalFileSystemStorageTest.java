package dev.sandoretti.ukun.api.storage.infra;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sandoretti.ukun.api.storage.application.StorageProperties;
import java.io.IOException;
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

    private static StorageProperties propertiesFor(Path localRoot) {
        StorageProperties properties = new StorageProperties();
        properties.setLocalRoot(localRoot);

        return properties;
    }
}
