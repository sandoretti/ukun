package dev.sandoretti.ukun.api.storage.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import dev.sandoretti.ukun.api.catalog.application.CatalogProperties;
import dev.sandoretti.ukun.api.storage.FileStoragePort;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * The web half of {@code /media/products/**}: routing, status and headers. The port is mocked because
 * it is one of the three real boundaries the {@code testing} skill allows mocking — what a key
 * resolves to on disk is {@link dev.sandoretti.ukun.api.storage.infra.LocalFileSystemStorageTest}'s
 * subject, not this one's.
 *
 * <p>Standalone rather than a {@code @WebMvcTest} slice: this controller has no advice, no converter
 * and no security to wire in, so a context would only buy startup time. The counterpart is that
 * nothing here proves the bean is actually registered — the curl against the running stack does.
 */
class MediaControllerTest {

    private static final byte[] IMAGE_BYTES = "bytes".getBytes(StandardCharsets.UTF_8);

    /**
     * Built from the same property the catalogue composes {@code imageUrl} with, rather than typed
     * out. That is the whole bug this endpoint closes: {@code catalog} was handing the browser a URL
     * nobody served. If the prefix and the mapping ever drift apart, these tests 404 and say so.
     */
    private static final String MEDIA_BASE_PATH = new CatalogProperties().getMediaBasePath();

    private final FileStoragePort storage = mock(FileStoragePort.class);

    private final MockMvcTester mvc = MockMvcTester.of(new MediaController(storage));

    @Test
    @DisplayName("a stored image comes back with the type its extension names")
    void shouldServeAStoredImage() {
        given(storage.load("mesa-madera.jpg")).willReturn(Optional.of(new ByteArrayResource(IMAGE_BYTES)));

        assertThat(mvc.get().uri(MEDIA_BASE_PATH + "mesa-madera.jpg"))
                .hasStatusOk()
                .hasContentType(MediaType.IMAGE_JPEG)
                .hasHeader("X-Content-Type-Options", "nosniff")
                .hasHeader("Cache-Control", "max-age=31536000, public, immutable");
    }

    @Test
    @DisplayName("the type follows the key, not a default")
    void shouldServeWebpAsWebp() {
        given(storage.load("8f2c1e40.webp")).willReturn(Optional.of(new ByteArrayResource(IMAGE_BYTES)));

        assertThat(mvc.get().uri(MEDIA_BASE_PATH + "8f2c1e40.webp"))
                .hasStatusOk()
                .hasContentType(MediaType.valueOf("image/webp"));
    }

    /**
     * Empty body, not a {@code ProblemDetail}. The caller is an {@code <img>} tag and cannot read one,
     * and rendering it would drag {@code shared} into a module that has no other reason to depend on
     * it (ADR-0017 §1).
     */
    @Test
    @DisplayName("an unknown key is a bare 404, with no ProblemDetail")
    void shouldAnswer404WithoutABodyForAnUnknownKey() {
        given(storage.load(any())).willReturn(Optional.empty());

        assertThat(mvc.get().uri(MEDIA_BASE_PATH + "no-existe.jpg"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .body()
                .isEmpty();
    }
}
