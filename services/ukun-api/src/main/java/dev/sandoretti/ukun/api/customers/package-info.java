/**
 * Customer profile: personal data, addresses, cards and favourite store (RF-05), and the data side of
 * the signup saga (RF-06). Owns: {@code customer} and the profile's {@code address} and {@code card}.
 *
 * <p>Depends on {@code stores} for the favourite store and on {@code identity} for the account behind
 * the customer (ADR-0017 §1).
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"stores", "identity", "shared", "audit"}
)
package dev.sandoretti.ukun.api.customers;
