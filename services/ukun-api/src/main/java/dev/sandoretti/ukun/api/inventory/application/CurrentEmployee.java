package dev.sandoretti.ukun.api.inventory.application;

/**
 * The employee behind the current request, resolved server-side, and the store the request is allowed
 * to touch.
 *
 * <p>Both values come from {@code employee} keyed by {@code account_id}, never from the request. That
 * is the whole point of {@code docs/08-identidad-y-seguridad.md} §4 and the reason {@code storeId} is
 * deliberately absent from the token: an employee's transfer to another store takes effect on their
 * next request rather than when their token expires.
 */
record CurrentEmployee(Long employeeId, Long storeId) {}
