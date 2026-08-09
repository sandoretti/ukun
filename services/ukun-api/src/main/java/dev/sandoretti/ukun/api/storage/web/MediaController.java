package dev.sandoretti.ukun.api.storage.web;

import dev.sandoretti.ukun.api.storage.FileStoragePort;
import io.swagger.v3.oas.annotations.Hidden;
import java.time.Duration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the product photos the catalogue points at. Public and anonymous, like the catalogue itself.
 *
 * <p><strong>Why this lives in {@code ukun-api} and not in the gateway.</strong>
 * {@code docs/08-identidad-y-seguridad.md} §9 calls {@code /media/**} "a public route of the gateway,
 * adapter of {@code FileStoragePort}" — two halves of one sentence that belong to two services. The
 * gateway owns the <em>route</em>: it declares the prefix public and forwards it (v0.5, issue #27).
 * The <em>adapter</em> is here, because it is the thing that becomes an S3 client in v1.1 and because
 * duplicating storage configuration into a second service to save one hop would be the wrong trade.
 *
 * <p><strong>Not an operation of the contract.</strong> Hence {@code @Hidden}: the same §9 says this
 * path is static content and is deliberately absent from {@code docs/api/openapi/ukun-api.yaml}. Drop
 * the annotation and it appears in {@code /v3/api-docs}, which CI diffs against the frozen document.
 *
 * <p>A 404 here carries no {@code ProblemDetail}, which is the one deviation from
 * {@code docs/12-error-contract.md} and is deliberate: the caller is an {@code <img>} tag, not the
 * generated API client. It is also what keeps this module free of any dependency on {@code shared},
 * so {@code storage} stays a leaf of the module graph (ADR-0017 §1).
 */
@Hidden
@RestController
@RequestMapping("/media/products")
class MediaController {

    /**
     * The key is content-addressed for practical purposes — RF-10 has the server generate a fresh one
     * per upload and never rewrite a file in place — so a stale cache is not a failure mode here.
     */
    private static final CacheControl IMMUTABLE_FOR_A_YEAR =
            CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

    /** Not in {@code HttpHeaders}, which only names the headers Spring itself handles. */
    private static final String CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";

    private final FileStoragePort storage;

    MediaController(FileStoragePort storage) {
        this.storage = storage;
    }

    /**
     * {@code {key}} matches a single path segment, so a slash cannot reach the port through it — and
     * the port validates the key again anyway, because that is where the invariant belongs and not in
     * a mapping that a later refactor could widen.
     *
     * <p>The type is derived from the extension of the stored key, which is safe precisely because the
     * key is not user-supplied text: RF-10 has the server derive that extension from the magic number
     * of the uploaded file (`docs/08-identidad-y-seguridad.md` §9). {@code nosniff} then keeps the
     * browser from second-guessing it.
     */
    @GetMapping("/{key}")
    ResponseEntity<Resource> serveProductImage(@PathVariable String key) {
        return storage.load(key)
                .map(image -> imageResponse(key, image))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static ResponseEntity<Resource> imageResponse(String key, Resource image) {
        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(key).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .cacheControl(IMMUTABLE_FOR_A_YEAR)
                .header(CONTENT_TYPE_OPTIONS, "nosniff")
                .body(image);
    }
}
