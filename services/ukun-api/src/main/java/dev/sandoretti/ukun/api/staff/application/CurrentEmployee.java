package dev.sandoretti.ukun.api.staff.application;

/**
 * The employee behind the current request, resolved server-side: who they are and where they work.
 *
 * <p>Both values come from {@code employee} keyed by {@code account_id}, never from the request. That
 * is the whole point of {@code docs/08-identidad-y-seguridad.md} §4 and the reason {@code storeId} is
 * deliberately absent from the token: an employee's transfer to another store takes effect on their
 * next request rather than when their token expires.
 *
 * <p>A record and not the {@code Employee} entity, even though this never leaves the module: what the
 * two use cases need is these two identifiers, and handing them the row instead would let a caller
 * read a name or an email — and, with {@code open-in-view: false}, do it outside the transaction that
 * loaded them.
 *
 * <p>{@code inventory} has a record of the same name and the same two fields, and they are not shared
 * on purpose: sharing one would mean declaring {@code inventory → staff}, an edge that is not in
 * ADR-0017 §1 and that a slice does not get to add. See {@code EmployeeStoreRepository}.
 */
record CurrentEmployee(Long employeeId, Long storeId) {}
