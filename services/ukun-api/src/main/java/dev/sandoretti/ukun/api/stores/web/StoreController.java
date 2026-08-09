package dev.sandoretti.ukun.api.stores.web;

import dev.sandoretti.ukun.api.stores.application.StoreQueryService;
import dev.sandoretti.ukun.api.stores.application.StoreResponse;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/stores} (RF-02). No authentication: like the catalog, it is an anonymous, cacheable
 * read — a shop's addresses and phone numbers are on its website.
 *
 * <p>A flat array and not a {@code PageResponse}, as the contract's preamble states for closed
 * reference data: a handful of rows the SPA caches whole.
 *
 * <p>It delegates and nothing else — no {@code @Transactional} (that belongs to the service), no
 * repository (ArchUnit watches that), no {@code if (x == null)}.
 */
@RestController
@RequestMapping("/api/stores")
class StoreController {

    private final StoreQueryService stores;

    StoreController(StoreQueryService stores) {
        this.stores = stores;
    }

    /**
     * The method name is the {@code operationId} springdoc publishes; the SPA's generated client is
     * named after it.
     *
     * <p>A plain {@code @RequestParam} rather than a parameter object, so no {@code @ParameterObject}
     * either: springdoc already publishes a bare parameter under its own name, and the annotation only
     * earns its place when an object is bound (the drift of {@code 33d6f19}).
     *
     * <p>{@code @Size(min = 1, …)} and not {@code @NotBlank}, which reads better and publishes worse:
     * springdoc derives the schema from {@code @Size}, and a bare {@code @Size(max = 255)} would emit
     * the default {@code minLength: 0} and stop matching the frozen contract while the endpoint kept
     * working. The bounds here are the contract's, transcribed.
     *
     * <p>No {@code @Validated} on the class: since Spring Framework 6.1 a controller method with a
     * constrained parameter is validated on its own, and a violation arrives as a
     * {@code HandlerMethodValidationException} that the advice already renders as the same
     * {@code VALIDATION_FAILED} {@code ProblemDetail} a rejected filter object produces.
     */
    @GetMapping
    List<StoreResponse> listStores(@RequestParam(required = false) @Size(min = 1, max = 255) String q) {
        return stores.list(q);
    }
}
