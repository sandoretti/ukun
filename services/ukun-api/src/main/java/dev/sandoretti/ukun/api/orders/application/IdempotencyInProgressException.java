package dev.sandoretti.ukun.api.orders.application;

import dev.sandoretti.ukun.api.shared.error.ErrorCode;
import dev.sandoretti.ukun.api.shared.error.UkunException;
import java.util.Map;
import java.util.UUID;

/**
 * {@code IDEMPOTENCY_IN_PROGRESS} (409): another request is confirming this same attempt right now
 * (ADR-0016).
 *
 * <p>It is what a genuine double submit gets — two requests in the same instant, one of which is
 * inside the transaction. There is no stored response to replay yet, and blocking until the other one
 * finishes would hold a connection open to answer a question that will be answerable by simply asking
 * again.
 *
 * <p>Two paths raise it: finding an {@code IN_PROGRESS} row, and losing the race to insert one
 * ({@code ux_idempotency_customer_key}). The second is the interesting one — it is the database, not
 * this service, that arbitrates between two transactions that both looked and both saw nothing.
 */
public final class IdempotencyInProgressException extends UkunException {

    private final UUID key;

    public IdempotencyInProgressException(UUID key) {
        super("Another request is already confirming attempt %s".formatted(key));
        this.key = key;
    }

    @Override
    public ErrorCode getCode() {
        return ErrorCode.IDEMPOTENCY_IN_PROGRESS;
    }

    @Override
    public Map<String, Object> getExtensions() {
        return Map.of("key", key.toString());
    }
}
