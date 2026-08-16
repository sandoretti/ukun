package dev.sandoretti.ukun.api.catalog.web;

import dev.sandoretti.ukun.api.catalog.application.ProductDetailResponse;
import dev.sandoretti.ukun.api.catalog.application.ProductFilters;
import dev.sandoretti.ukun.api.catalog.application.ProductQueryService;
import dev.sandoretti.ukun.api.catalog.application.ProductSuggestionResponse;
import dev.sandoretti.ukun.api.catalog.application.ProductSummaryResponse;
import dev.sandoretti.ukun.api.shared.openapi.BadRequestResponse;
import dev.sandoretti.ukun.api.shared.openapi.InternalErrorResponse;
import dev.sandoretti.ukun.api.shared.openapi.NotFoundResponse;
import dev.sandoretti.ukun.api.shared.web.PageResponse;
import dev.sandoretti.ukun.api.shared.web.PaginationParams;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public catalog (RF-01): the listing, the typeahead and the product detail. No authentication —
 * these are the anonymous, cacheable reads of the shop.
 *
 * <p><strong>{@code /suggest} and {@code /{id}} share a path segment.</strong> {@code PathPattern}
 * prefers the literal over the template, so {@code GET /api/products/suggest} reaches the typeahead
 * and not {@code getProduct} with an unparseable id — which would come back as a {@code 400}. It is
 * settled by a test rather than by declaration order, because declaration order does not decide it and
 * a comment cannot notice when it stops being true.
 *
 * <p>It delegates and nothing else. It holds no {@code @Transactional} (that belongs to the service),
 * it never touches a repository (ArchUnit watches that), and it contains no {@code if (x == null)} —
 * a service either returns a value or throws a typed exception, and the advice renders it.
 *
 * <p>Both parameter objects are bound from the query string and validated. Their bounds are the
 * contract's, and the {@code 400} they produce comes back as a {@code ProblemDetail} with
 * {@code code: VALIDATION_FAILED} and one {@code errors[]} entry per offending field.
 *
 * <p>{@code @ParameterObject} is not decoration: without it springdoc documents the two objects as
 * two opaque query parameters called {@code filters} and {@code pagination}, so the
 * {@code /v3/api-docs} that CI diffs against the frozen contract stops matching it — while the
 * endpoint keeps working, which is exactly what makes that drift silent.
 *
 * <p>{@code produces} and the error responses are of that same family: {@code application/json} is the
 * only representation the contract declares, and the statuses each operation can answer with are the
 * contract's too, transcribed one by one because springdoc cannot see the advice that renders them
 * ({@link BadRequestResponse}).
 */
@RestController
@RequestMapping(path = "/api/products", produces = MediaType.APPLICATION_JSON_VALUE)
class ProductController {

    private final ProductQueryService products;

    ProductController(ProductQueryService products) {
        this.products = products;
    }

    @GetMapping
    @ApiResponse(responseCode = "200", description = "A page of products.")
    @BadRequestResponse
    @InternalErrorResponse
    PageResponse<ProductSummaryResponse> listProducts(
            @Valid @ParameterObject ProductFilters filters, @Valid @ParameterObject PaginationParams pagination) {

        return products.list(filters, pagination);
    }

    /**
     * The typeahead of the search box. A flat {@code List}, never a {@code PageResponse}: the contract
     * says so to keep the {@code COUNT} off an endpoint that fires on every keystroke.
     *
     * <p>Two plain {@code @RequestParam}s and no parameter object, which is why there is no
     * {@code @ParameterObject} here either — springdoc already publishes them under their own names,
     * and the annotation is only needed when an object is bound. {@code q} is required because
     * {@code @RequestParam} is required by default; {@code limit} carries the contract's default of
     * {@code 8}, resolved by Spring before validation ever sees it.
     *
     * <p>The constraints are the contract's bounds, transcribed. Spring validates them and reports a
     * {@code HandlerMethodValidationException}, which the advice renders as the same
     * {@code VALIDATION_FAILED} {@code ProblemDetail} a rejected filter object produces — the frontend
     * cannot tell, and should not have to tell, which of the two it hit.
     *
     * <p>{@code @Size(min = 1, …)} and not {@code @NotBlank}, which reads better and publishes worse:
     * springdoc derives the schema from {@code @Size}, so a bare {@code @Size(max = 255)} emits the
     * default {@code minLength: 0} and the generated document stops matching the frozen contract while
     * the endpoint keeps working. That is the drift of {@code 33d6f19} in its other shape. Rejecting a
     * whitespace-only term is left out for the same reason: the contract says {@code minLength: 1}, and
     * validating something the published document declares valid is a divergence too.
     */
    @GetMapping("/suggest")
    @ApiResponse(responseCode = "200", description = "Suggestions, at most `limit` of them.")
    @BadRequestResponse
    @InternalErrorResponse
    List<ProductSuggestionResponse> suggestProducts(
            @RequestParam @Size(min = 1, max = 255) String q,
            @RequestParam(defaultValue = "8") @Min(1) @Max(10) int limit) {

        return products.suggest(q, limit);
    }

    /**
     * The product detail. There is no {@code if (product == null)} here and there never will be: the
     * service throws {@link dev.sandoretti.ukun.api.shared.error.ResourceNotFoundException} and the
     * advice turns it into the {@code ProblemDetail} of {@code docs/12-error-contract.md} with
     * {@code code: RESOURCE_NOT_FOUND}.
     *
     * <p>The method name is the {@code operationId} of the contract, because that is what springdoc
     * publishes by default and what the frontend's generated client is named after. Renaming it
     * renames a function in the SPA.
     */
    @GetMapping("/{id}")
    @ApiResponse(responseCode = "200", description = "The product.")
    @NotFoundResponse
    @InternalErrorResponse
    ProductDetailResponse getProduct(@PathVariable Long id) {
        return products.detail(id);
    }

    /**
     * The product detail by SKU (RF-09): what the employee resolves before creating a stock line with
     * {@code POST /api/stores/{storeId}/stock} (ADR-0015). Same response as {@link #getProduct(Long)},
     * a different key.
     *
     * <p>It does not collide with {@code /{id}}, unlike {@code /suggest}: the path has one segment more,
     * so no pattern of this controller is a candidate for the other's requests. {@code /api/products/by-sku}
     * on its own does reach {@code getProduct} with an unparseable id and comes back as the {@code 400}
     * that a missing path variable deserves, which is the contract's shape for a path that requires one.
     *
     * <p><strong>No {@code @Pattern} on the SKU, deliberately.</strong> The contract declares exactly
     * three responses here — {@code 200}, {@code 404} and {@code 500} — so rejecting a malformed code
     * with a {@code 400} would be an answer the frozen document does not allow, and the frontend
     * generated from it would not know what to do with. A string that cannot be a SKU is a string no
     * product has: it resolves to the same {@code 404 RESOURCE_NOT_FOUND} as one that merely does not
     * exist, which is both the contract's shape and the answer that leaks least. The {@code @Schema} is
     * still published so Swagger UI and the frozen {@code Sku} schema say the same thing.
     */
    @GetMapping("/by-sku/{sku}")
    @ApiResponse(responseCode = "200", description = "The product.")
    @NotFoundResponse
    @InternalErrorResponse
    ProductDetailResponse getProductBySku(@PathVariable @Schema(pattern = "^[A-Z0-9-]{3,32}$") String sku) {
        return products.detailBySku(sku);
    }
}
