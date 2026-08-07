/**
 * Binary file storage behind {@code FileStoragePort} — filesystem adapter today, object storage in
 * v1.1. Owns no table.
 *
 * <p>One of the two real external boundaries of this service, hence a module without any data of its
 * own (ADR-0017 §1).
 */
@org.springframework.modulith.ApplicationModule
package dev.sandoretti.ukun.api.storage;
