package dev.sandoretti.ukun.api.orders.application;

import dev.sandoretti.ukun.api.shared.error.ErrorCode;
import dev.sandoretti.ukun.api.shared.error.UkunException;
import java.util.Map;
import java.util.UUID;

/**
 * {@code IDEMPOTENCY_MISMATCH} (422): this key was already used against a different cart (ADR-0016).
 *
 * <p>Normal use of the SPA cannot produce it — the attempt is issued by the server and echoed straight
 * back — so it means an {@code attemptId} is being reused for an order it was not opened for. Serving
 * the stored response would confirm an order the caller did not ask for; recomputing would break the
 * promise that one key means one order. Both are worse than saying so.
 */
public final class IdempotencyMismatchException extends UkunException {

    private final UUID key;

    public IdempotencyMismatchException(UUID key) {
        super("Attempt %s was already used for a different request".formatted(key));
        this.key = key;
    }

    @Override
    public ErrorCode getCode() {
        return ErrorCode.IDEMPOTENCY_MISMATCH;
    }

    @Override
    public Map<String, Object> getExtensions() {
        return Map.of("key", key.toString());
    }
}
