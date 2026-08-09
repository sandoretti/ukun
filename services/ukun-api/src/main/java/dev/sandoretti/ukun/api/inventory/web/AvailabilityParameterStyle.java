package dev.sandoretti.ukun.api.inventory.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.List;
import java.util.Optional;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

/**
 * Publishes {@code products} of {@code GET /api/availability} the way the frozen contract declares it:
 * {@code style: form, explode: false} — one comma-separated parameter.
 *
 * <p><strong>Why this is a bean and not an annotation.</strong> springdoc 3.0.3 does not emit
 * {@code explode} for a {@code @RequestParam} at all: neither {@code @Parameter(explode = FALSE)} nor
 * {@code @Parameter(explode = TRUE)} reaches the document, verified by reading it back from
 * {@code /v3/api-docs} with each in turn. The attribute is simply absent — and an absent
 * {@code explode} under the default {@code form} style means {@code true} in OpenAPI, which is the
 * opposite of what the contract says.
 *
 * <p><strong>Why it is worth a bean.</strong> Nothing would fail without it. Spring binds
 * {@code ?products=1,2} and {@code ?products=1&products=2} identically, so a client generated from the
 * drifted document would work perfectly while the two sides of the contract disagreed — and the
 * contract has been frozen since SINC-1, so the document is what has to move. That silence is the same
 * failure mode as {@code 33d6f19}, where two bound objects were published as two opaque parameters and
 * the endpoint went on working.
 *
 * <p>It is scoped to one operation and one parameter on purpose. A customizer that reached across the
 * document would be a second place where the contract is decided, next to the YAML that already is
 * one.
 */
@Component
class AvailabilityParameterStyle implements OpenApiCustomizer {

    private static final String PATH = "/api/availability";
    private static final String PARAMETER = "products";

    @Override
    public void customise(OpenAPI openApi) {
        parametersOf(openApi).stream()
                .filter(parameter -> PARAMETER.equals(parameter.getName()))
                .forEach(parameter -> {
                    parameter.setStyle(Parameter.StyleEnum.FORM);
                    parameter.setExplode(false);
                });
    }

    /**
     * An empty list when the path is not in the document, which happens in any context where this
     * endpoint is not mapped. A customizer that threw there would turn {@code /v3/api-docs} into a
     * {@code 500} over a parameter's serialisation style.
     */
    private List<Parameter> parametersOf(OpenAPI openApi) {
        return Optional.ofNullable(openApi.getPaths())
                .map(paths -> paths.get(PATH))
                .map(PathItem::getGet)
                .map(Operation::getParameters)
                .orElseGet(List::of);
    }
}
