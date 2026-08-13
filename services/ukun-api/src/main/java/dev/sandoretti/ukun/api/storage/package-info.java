/**
 * Binary file storage behind {@code FileStoragePort} — filesystem adapter today, object storage in
 * v1.1. Owns no table.
 *
 * <p>One of the two real external boundaries of this service, hence a module without any data of its
 * own (ADR-0017 §1).
 *
 * <p><strong>The read half was brought forward from v0.7 to v0.3</strong>, because {@code catalog}
 * handed the browser a servable {@code imageUrl} from the first release of the catalogue and nothing
 * was answering it — every product photo was a 404. The write half arrived in v0.7 with RF-10, and it
 * is the two halves of the file and nothing else: multipart parsing, the 5 MB limit and the check that
 * an uploaded file really is an image are {@code catalog}'s, because they are rules about product
 * photos rather than about storing bytes.
 *
 * <p>Declares no {@code allowedDependencies} — an unrestricted list — and depends on nothing, not even
 * {@code shared}: it is a leaf of the module graph and stays one.
 */
@org.springframework.modulith.ApplicationModule
package dev.sandoretti.ukun.api.storage;
