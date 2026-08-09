/**
 * OAuth2: the Authorization Server itself — registered clients, the signing key and the JWKS, the
 * customisation of the access token's claims, and (from the next commit) the login form, the
 * authentication events and the login attempt limit (ADR-0017 §4).
 *
 * <p>Reaches {@code accounts} only through {@code AccountsApi}. Injecting {@code AccountRepository}
 * from here — or touching the {@code Account} entity — breaks {@code ApplicationModules.verify()},
 * and that is the point of the boundary: what leaves {@code accounts} is a record, never a row
 * (docs/14-modules.md §2).
 *
 * <p><strong>No claim of this token ever carries a {@code storeId}.</strong> It would save a lookup
 * per stock operation and cost three things: {@code ukun-auth} would have to know what a store is,
 * moving an employee between stores would not take effect until their token expired, and a business
 * authorisation decision would be frozen into a credential (docs/08-identidad-y-seguridad.md §4).
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = "accounts")
package dev.sandoretti.ukun.auth.oauth2;
