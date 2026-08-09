package dev.sandoretti.ukun.api.inventory.web;

import dev.sandoretti.ukun.api.inventory.application.AvailabilityQueryService;
import dev.sandoretti.ukun.api.inventory.application.AvailabilityResponse;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/availability} (RF-01). Anonymous, like the catalog it accompanies — but, unlike the
 * catalog, not cacheable for long: this is the half of the read that changes on every purchase, which
 * is part of why ADR-0017 §3 separated the two.
 *
 * <p>It lives in {@code inventory} and not in {@code catalog}. That is the whole reason the endpoint
 * exists as a separate URL rather than as a field on the product row.
 *
 * <p>It delegates and nothing else — no {@code @Transactional}, no repository, no null checks.
 */
@RestController
@RequestMapping("/api/availability")
class AvailabilityController {

    private final AvailabilityQueryService availability;

    AvailabilityController(AvailabilityQueryService availability) {
        this.availability = availability;
    }

    /**
     * The method name is the {@code operationId} springdoc publishes.
     *
     * <p>Both parameters are required, which for {@code @RequestParam} is the default: a missing one
     * is a {@code MissingServletRequestParameterException} the advice renders as
     * {@code VALIDATION_FAILED}, the same {@code ProblemDetail} shape as a rejected bound object.
     *
     * <p>{@code products} arrives comma-separated ({@code style: form, explode: false} in the
     * contract), which Spring binds into a {@code List<Long>} without help. The {@code @Size} bounds
     * are the contract's {@code minItems}/{@code maxItems}, transcribed — and the upper one is not
     * decoration: it is what stops a single request from turning an indexed batch look-up into a scan
     * of the table, on an endpoint that needs no authentication to reach.
     *
     * <p>How {@code products} is serialised is <strong>not</strong> declared here — see
     * {@link AvailabilityParameterStyle}, which is where springdoc's one blind spot on this endpoint is
     * patched and explained.
     *
     * <p>Two bare parameters and no parameter object, so no {@code @ParameterObject}: springdoc
     * publishes them under their own names already, and the annotation only earns its place when an
     * object is bound.
     */
    @GetMapping
    AvailabilityResponse getAvailability(
            @RequestParam Long store, @RequestParam @Size(min = 1, max = 100) List<Long> products) {

        return availability.availability(store, products);
    }
}
