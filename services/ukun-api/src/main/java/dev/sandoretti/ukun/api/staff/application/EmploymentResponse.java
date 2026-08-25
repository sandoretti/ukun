package dev.sandoretti.ukun.api.staff.application;

/**
 * The body of {@code GET /api/me/employment} — the {@code Employment} schema of the contract. Which
 * store the employee behind this token works in, which is what the back-office of RF-09 needs before
 * it can build any of its four routes: they all carry {@code {storeId}} in the path.
 *
 * <p>It is a resource of its own rather than a field on {@code CurrentUserResponse}, and the reason is
 * structural: {@code GET /api/me} is served by {@code customers}, and making that module read employee
 * data would cross an edge {@code ApplicationModules.verify()} rejects (ADR-0017 §1). The shared
 * {@code /api/me} prefix is a fact about the URL space and not about the decomposition into modules
 * (ADR-0020, discarded alternatives).
 *
 * <p><strong>{@code storeId} is an answer, never a question.</strong> The screens that receive it use
 * it to address stock resources; they do not send it back as proof of anything, and this service would
 * not believe them if they did — every stock operation resolves the store from {@code account_id}
 * again ({@code docs/08-identidad-y-seguridad.md} §4).
 *
 * <p>{@code storeName} is here so the back-office can say <em>where</em> the person is working without
 * a second round trip to {@code GET /api/stores}. It is the only field of the three that this module
 * does not own: it comes from {@code StoresApi}, as a {@code record} and never as a {@code Store}
 * ({@code docs/14-modules.md} §2).
 *
 * <p>No name, no email and no {@code isAdmin}: {@code GET /api/me} already answers who the person is
 * and what roles their token carries, and a second copy of that here is a second thing to keep in
 * step. This answers one question, which is the one its name asks.
 */
public record EmploymentResponse(Long employeeId, Long storeId, String storeName) {}
