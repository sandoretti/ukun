package dev.sandoretti.ukun.api.customers;

/**
 * What the {@code customers} module offers the rest of the application: who the customer behind the
 * current request is. The only type here other modules may import ({@code docs/14-modules.md} §1).
 *
 * <p><strong>Why this is not a method of {@code IdentityApi}.</strong> {@code identity} knows accounts,
 * not customers — a {@code customer_id} is a row of a table this module owns, and the crossing from one
 * to the other is the unique index {@code ux_customer_account_id}. It also has to be here for a
 * structural reason: {@code cart} and {@code orders} do not list {@code identity} among their allowed
 * dependencies (ADR-0017 §1), so this interface is the whole of how they learn whose cart they are
 * touching.
 */
public interface CustomersApi {

    /**
     * The customer behind the token on the current request.
     *
     * <p>This is the rule of {@code docs/08-identidad-y-seguridad.md} §4 made into a method: ownership
     * is resolved server-side from {@code account_id}, and there is no overload taking an identifier
     * from the request, because an identifier in a request identifies a resource and never authorises
     * access to it. That is the legacy antipattern — a {@code tiendaId} arriving as a query parameter
     * and being taken at its word.
     *
     * @throws IllegalStateException if the token holds the {@code CUSTOMER} role with no customer row
     *     behind it. Same judgement as {@code MyProfileService.view}: the signup saga activates an
     *     account only once its profile exists, so this is a broken invariant of this service rather
     *     than a caller's mistake, and a {@code 500} with a stack trace is what gets it fixed.
     * @throws org.springframework.security.authentication.AuthenticationCredentialsNotFoundException if
     *     there is no authenticated account at all, which means the route was left out of the filter
     *     chain's protected set.
     */
    Long requireCurrentCustomerId();
}
