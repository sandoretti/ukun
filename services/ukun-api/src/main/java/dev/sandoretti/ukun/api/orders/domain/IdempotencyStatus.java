package dev.sandoretti.ukun.api.orders.domain;

/**
 * Where a confirmation attempt got to (ADR-0016). Two values, and they answer two different questions
 * a retry can ask.
 *
 * <ul>
 *   <li>{@link #IN_PROGRESS} — somebody is inside the transaction right now. A second request with the
 *       same key gets {@code 409 IDEMPOTENCY_IN_PROGRESS}: there is no stored answer to replay yet, and
 *       waiting for one would hold a connection open on a guess.
 *   <li>{@link #COMPLETED} — the order exists and its response is stored. Any number of retries get
 *       that same response back, with the effect not repeated.
 * </ul>
 *
 * <p>There is no {@code FAILED}: a checkout that fails rolls its transaction back, and the row goes
 * with it. That is deliberate — a customer whose order was rejected for lack of stock can try the same
 * attempt again once it is back, and a tombstone would turn a recoverable {@code 409} into a permanent
 * one.
 */
public enum IdempotencyStatus {
    IN_PROGRESS,
    COMPLETED
}
