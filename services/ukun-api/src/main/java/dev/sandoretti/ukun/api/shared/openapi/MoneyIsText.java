package dev.sandoretti.ukun.api.shared.openapi;

import com.fasterxml.jackson.databind.JavaType;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Publishes every amount as the {@code Money} schema of the contract declares it — a decimal
 * <strong>string</strong> — instead of the JSON number springdoc infers from {@code BigDecimal}.
 *
 * <p><strong>This was a live drift, not a hypothetical one.</strong>
 * {@link dev.sandoretti.ukun.api.shared.web.Money @Money} already makes Jackson serialise the amount
 * as text, so {@code GET /api/products} really answers {@code "price": "399.99"} — while
 * {@code /v3/api-docs} declared {@code type: number}. A client generated from that document would have
 * parsed the price into an IEEE-754 double and lost cents, which is defect #5 handed to the frontend.
 * Nothing failed; the two sides of the contract simply disagreed, and only
 * {@code OpenApiDriftIT} noticed.
 *
 * <p><strong>Why {@code BigDecimal} and not the {@code @Money} annotation.</strong> A
 * {@link ModelConverter} sees the type, not the annotations Jackson reads, and this API has exactly one
 * decimal: money. The two query filters {@code priceMin}/{@code priceMax} are the proof that keying on
 * the annotation would be worse — they are bound from the query string, Jackson never sees them, they
 * carry no {@code @Money}, and the contract still declares them as {@code Money}. If a decimal that is
 * not an amount ever appears, this is the one place where the rule has to be narrowed.
 *
 * <p>The numeric bounds go with the type: {@code minimum} on a string means nothing, and Bean
 * Validation still enforces the real one on the request.
 */
@Component
class MoneyIsText implements ModelConverter {

    @Override
    public Schema<?> resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        Schema<?> schema = chain.hasNext() ? chain.next().resolve(type, context, chain) : null;

        if (schema == null || !isDecimal(type)) {
            return schema;
        }

        schema.setType("string");
        if (schema.getTypes() != null) {
            schema.setTypes(new LinkedHashSet<>(Set.of("string")));
        }
        schema.setFormat(null);
        schema.setMinimum(null);
        schema.setMaximum(null);

        return schema;
    }

    private static boolean isDecimal(AnnotatedType type) {
        JavaType resolved = type.getType() == null ? null : Json.mapper().constructType(type.getType());

        return resolved != null && BigDecimal.class.equals(resolved.getRawClass());
    }
}
