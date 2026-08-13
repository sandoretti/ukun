package dev.sandoretti.ukun.api.staff.application;

import jakarta.validation.constraints.Size;

/**
 * The two optional filters of {@code GET /api/admin/employees} (RF-11): free text over name, last name
 * and email, and the administrator flag of RF-12. One that was not sent is not applied.
 *
 * <p><strong>There is no store component and there must not be one.</strong> The listing is always
 * scoped to the admin's own store, resolved from their {@code account_id}; a filter for it would be an
 * invitation to ask for somebody else's ({@code docs/08-identidad-y-seguridad.md} §4).
 *
 * <p>It lives next to the use case rather than in {@code web/}, like every other filter object of this
 * API: it is the input of the query, not a detail of HTTP.
 */
public record EmployeeFilters(@Size(min = 1, max = 255) String q, Boolean isAdmin) {}
