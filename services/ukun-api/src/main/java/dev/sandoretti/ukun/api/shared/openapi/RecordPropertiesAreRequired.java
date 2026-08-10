package dev.sandoretti.ukun.api.shared.openapi;

import com.fasterxml.jackson.databind.JavaType;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;
import java.util.ArrayList;
import java.util.Iterator;
import org.springframework.stereotype.Component;

/**
 * Publishes every property of a DTO {@code record} of this service as {@code required}, which is the
 * convention the frozen contract follows without exception: <strong>a key is always present, and
 * "optional" is expressed by admitting {@code null}</strong> ({@code imageUrl} and {@code description}
 * are the two cases in v0.3, and they are {@code required} there too, typed
 * {@code [string, 'null']}).
 *
 * <p>Left to itself springdoc marks a property required only when it is a primitive or carries
 * {@code @NotNull}, so the generated document declared <em>nothing</em> required. That is not a
 * cosmetic difference: {@code openapi-typescript} turns an optional property into {@code price?:
 * string}, and the SPA would then guard against an absence that never happens while the compiler said
 * nothing about the {@code null} that does.
 *
 * <p><strong>Why a bean and not {@code @Schema(requiredMode = REQUIRED)} on every component.</strong>
 * Because it is a convention of the whole contract, and forty annotations repeating it would be forty
 * chances to forget one — with the reminder arriving as a failing drift check rather than as a rule.
 * The rule is stated once, here, and it is scoped to the records of this service so that neither
 * Spring's own types nor a library's leak into it.
 *
 * <p>The day a request DTO needs a genuinely optional property (a partial update), that is where this
 * rule gets narrowed — with the contract in hand, since the contract is what decides it.
 */
@Component
class RecordPropertiesAreRequired implements ModelConverter {

    private static final String DTO_PACKAGE = "dev.sandoretti.ukun.api";

    @Override
    public Schema<?> resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        Schema<?> schema = chain.hasNext() ? chain.next().resolve(type, context, chain) : null;

        if (schema == null || !isOurRecord(type)) {
            return schema;
        }

        // A named schema comes back as a $ref: what has to be marked is the model the reference points
        // at, which the resolution above has just registered in the context.
        Schema<?> model = schema.get$ref() == null ? schema : context.getDefinedModels().get(nameOf(schema.get$ref()));
        if (model != null && model.getProperties() != null) {
            model.setRequired(new ArrayList<>(model.getProperties().keySet()));
        }

        return schema;
    }

    private static boolean isOurRecord(AnnotatedType type) {
        JavaType resolved = type.getType() == null ? null : Json.mapper().constructType(type.getType());
        Class<?> raw = resolved == null ? null : resolved.getRawClass();

        return raw != null && raw.isRecord() && raw.getPackageName().startsWith(DTO_PACKAGE);
    }

    private static String nameOf(String reference) {
        return reference.substring(reference.lastIndexOf('/') + 1);
    }
}
