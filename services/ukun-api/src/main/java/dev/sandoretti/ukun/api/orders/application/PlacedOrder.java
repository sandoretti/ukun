package dev.sandoretti.ukun.api.orders.application;

/**
 * The outcome of {@code POST /api/orders}: the order, and whether this request is the one that created
 * it.
 *
 * <p>The contract answers the same body with two different statuses — {@code 201} with a
 * {@code Location} the first time, {@code 200} on every replay — and the difference is not something
 * the controller can work out from the body. It is what the idempotency record said, so the service
 * that read it is the one that reports it.
 *
 * <p>A record rather than an exception for the replay path: replaying is a success. The customer
 * pressed the button twice, or their phone lost the network and retried, and both of those deserve
 * their order back rather than an error explaining that they already have one.
 */
public record PlacedOrder(boolean created, OrderResponse order) {

    static PlacedOrder created(OrderResponse order) {
        return new PlacedOrder(true, order);
    }

    static PlacedOrder replayed(OrderResponse order) {
        return new PlacedOrder(false, order);
    }
}
