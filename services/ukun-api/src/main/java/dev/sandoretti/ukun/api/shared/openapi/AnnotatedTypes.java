package dev.sandoretti.ukun.api.shared.openapi;

import com.fasterxml.jackson.databind.JavaType;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.util.Json;
import java.util.Optional;

/**
 * The one thing every {@link io.swagger.v3.core.converter.ModelConverter} of this package needs and
 * swagger does not offer: the Java class behind the type it is being asked to resolve.
 *
 * <p>The empty case is not defensive programming. swagger calls a converter with an
 * {@link AnnotatedType} whose {@code type} is {@code null} while it resolves anonymous and synthetic
 * schemas, so a converter that assumed a class there would turn {@code /v3/api-docs} into a
 * {@code 500}.
 */
final class AnnotatedTypes {

    private AnnotatedTypes() {}

    static Optional<Class<?>> rawClassOf(AnnotatedType type) {
        return Optional.ofNullable(type.getType())
                .map(Json.mapper()::constructType)
                .map(JavaType::getRawClass);
    }
}
