package dev.sandoretti.ukun.api.audit.infra;

import dev.sandoretti.ukun.api.audit.domain.AuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The trail's only writer. Internal to the {@code audit} module: nothing outside it reaches a row
 * except through {@code AuditTrail}.
 *
 * <p>No read method here, and that is not an oversight. The query {@code docs/11} §2 designs the two
 * indexes for —"everything that has happened to this resource"— has no endpoint in v0.7: ADR-0014
 * decides where the trail lives, not who reads it. Adding a finder before there is a caller would be
 * declaring an API by guessing at it.
 */
public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {}
