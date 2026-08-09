/**
 * Binary file storage behind {@code FileStoragePort} — filesystem adapter today, object storage in
 * v1.1. Owns no table.
 *
 * <p>One of the two real external boundaries of this service, hence a module without any data of its
 * own (ADR-0017 §1).
 *
 * <p><strong>Read half only, brought forward from v0.7 to v0.3.</strong> {@code catalog} hands the
 * browser a servable {@code imageUrl} from the first release of the catalogue, and nothing was
 * answering it — every product photo was a 404. So the port, the filesystem adapter and the public
 * {@code /media/products/**} route live here now; the upload of RF-10, with its multipart handling
 * and its 5 MB limit, stays in v0.7 where it was planned.
 *
 * <p>Declares no {@code allowedDependencies} — an unrestricted list — and depends on nothing, not even
 * {@code shared}: it is a leaf of the module graph and stays one.
 */
@org.springframework.modulith.ApplicationModule
package dev.sandoretti.ukun.api.storage;
