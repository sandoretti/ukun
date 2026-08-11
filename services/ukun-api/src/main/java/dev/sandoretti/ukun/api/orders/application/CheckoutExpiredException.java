package dev.sandoretti.ukun.api.orders.application;

import dev.sandoretti.ukun.api.shared.error.ErrorCode;
import dev.sandoretti.ukun.api.shared.error.UkunException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * {@code CHECKOUT_EXPIRED} (410): the attempt is past its {@code expiresAt}, or there is no such
 * attempt for this customer at all (ADR-0016).
 *
 * <p><strong>The two cases answer identically, and that is the decision.</strong> A missing attempt
 * and a stale one are the same instruction to the frontend — reopen the checkout with
 * {@code POST /api/checkout/attempts} — and telling them apart would answer "does this attemptId
 * exist?" for identifiers belonging to other people. The {@code expiredAt} extension is therefore
 * present only when there was an attempt to expire.
 *
 * <p>{@link LinkedHashMap} rather than {@code Map.of}, which rejects null values: the absent
 * {@code expiredAt} is the whole point of the paragraph above.
 */
public final class CheckoutExpiredException extends UkunException {

    private final UUID attemptId;
    private final Instant expiredAt;

    /** A stale attempt: it existed, and it is past its lifetime. */
    public static CheckoutExpiredException expired(UUID attemptId, Instant expiredAt) {
        return new CheckoutExpiredException(attemptId, expiredAt);
    }

    /** No attempt with this key belongs to this customer — see the class javadoc. */
    public static CheckoutExpiredException unknown(UUID attemptId) {
        return new CheckoutExpiredException(attemptId, null);
    }

    private CheckoutExpiredException(UUID attemptId, Instant expiredAt) {
        super("Checkout attempt %s is no longer valid".formatted(attemptId));
        this.attemptId = attemptId;
        this.expiredAt = expiredAt;
    }

    @Override
    public ErrorCode getCode() {
        return ErrorCode.CHECKOUT_EXPIRED;
    }

    @Override
    public Map<String, Object> getExtensions() {
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("attemptId", attemptId.toString());
        if (expiredAt != null) {
            extensions.put("expiredAt", expiredAt.toString());
        }

        return extensions;
    }
}
