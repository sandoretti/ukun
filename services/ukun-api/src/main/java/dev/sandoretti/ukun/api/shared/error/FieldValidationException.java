package dev.sandoretti.ukun.api.shared.error;

import java.util.List;
import java.util.Map;

/**
 * {@code VALIDATION_FAILED} for a rule Bean Validation cannot express, because checking it needs a
 * query rather than an annotation — "this store exists", "this SKU is not taken".
 *
 * <p>It carries the same {@code errors[]} extension a rejected form does, and that is the whole point:
 * the frontend paints a message under a field, and it must not have to tell apart a field rejected by
 * a {@code @Pattern} from one rejected by a lookup. Same {@code code}, same shape, same rendering
 * ({@code docs/12-error-contract.md} §2).
 *
 * <p>The alternative — letting the insert fail on a foreign key and mapping {@code 23503} to
 * {@code REFERENTIAL_INTEGRITY} — produces a {@code 409} with nothing to point at, on a request that
 * is really just a form with one bad value in it.
 */
public final class FieldValidationException extends UkunException {

    private final String field;
    private final String reason;

    public FieldValidationException(String field, String reason) {
        super("%s %s".formatted(field, reason));
        this.field = field;
        this.reason = reason;
    }

    @Override
    public ErrorCode getCode() {
        return ErrorCode.VALIDATION_FAILED;
    }

    @Override
    public Map<String, Object> getExtensions() {
        return Map.of("errors", List.of(new ValidationError(field, reason)));
    }
}
