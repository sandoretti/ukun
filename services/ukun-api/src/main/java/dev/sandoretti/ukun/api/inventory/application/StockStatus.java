package dev.sandoretti.ukun.api.inventory.application;

/**
 * Which half of the employee's stock screen is being asked for (RF-09).
 *
 * <p>{@code STOCKED} is what the store holds; {@code NOT_STOCKED} is what the catalogue offers that
 * this store has no line for yet — the "add new stock" list. One endpoint with a status rather than
 * two, because they are the same table read from either side of the same anti-join, and the frontend
 * paginates and filters both identically.
 */
public enum StockStatus {
    STOCKED,
    NOT_STOCKED
}
