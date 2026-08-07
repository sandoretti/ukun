/**
 * Physical stores and the resolution of the online store by its {@code is_online} flag (RF-02).
 * Owns: {@code store} and the store's {@code address}.
 *
 * <p>Depends on nothing: it is one of the roots of the module graph (ADR-0017 §1).
 */
@org.springframework.modulith.ApplicationModule
package dev.sandoretti.ukun.api.stores;
