package dev.sandoretti.ukun.api.shared.error;

import java.util.Map;

/**
 * {@code DUPLICATE_VALUE} (409): the value the client typed into a field that has to be unique is
 * already taken — {@code product.sku} and {@code product_type.name} today (RF-10, RF-13).
 *
 * <p><strong>Thrown from a check the use case makes before writing, never from a caught
 * {@code 23505}.</strong> The safety net in {@link GlobalExceptionHandler} only recognises a
 * foreign-key violation, so a unique-key one that reached the database would come out as a
 * {@code 500}; and a caught constraint violation can only name the constraint, while the frontend
 * needs the <em>field</em> to paint the message under the right input. The database keeps its unique
 * index either way — it is the guarantee, this is the experience.
 *
 * <p>It lives in {@code shared} for the reason {@link VersionConflictException} gives: uniqueness of a
 * business value is not a fact about the catalogue. The second module that needs it would otherwise
 * import across a boundary or write the class again.
 *
 * <p>{@code resource} is the contract's name for the thing ({@code product}, {@code productType}),
 * lower camel case as the registry spells it — not the Java class name, which is an implementation
 * detail the client never sees.
 */
public final class DuplicateValueException extends UkunException {

    private final String resource;
    private final String field;

    public DuplicateValueException(String resource, String field, Object value) {
        super("%s with %s %s already exists".formatted(resource, field, value));
        this.resource = resource;
        this.field = field;
    }

    @Override
    public ErrorCode getCode() {
        return ErrorCode.DUPLICATE_VALUE;
    }

    /**
     * The two the registry declares, and deliberately not the value itself: the client sent it and
     * already has it, and echoing user input back into a response body is a habit worth not starting.
     */
    @Override
    public Map<String, Object> getExtensions() {
        return Map.of("resource", resource, "field", field);
    }
}
