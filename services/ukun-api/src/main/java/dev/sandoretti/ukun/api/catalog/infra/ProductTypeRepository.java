package dev.sandoretti.ukun.api.catalog.infra;

import dev.sandoretti.ukun.api.catalog.domain.ProductType;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code product_type}, the other table the {@code catalog} module owns.
 *
 * <p>No method of its own: the public read is the inherited {@code findAll(Sort)}, and the sort comes
 * from the use case rather than from a derived query name so that the tie-breaker of defect #23 is
 * declared in one place.
 *
 * <p>An unbounded {@code findAll} looks like a breach of rule 8 of the skill {@code spring-api} and is
 * the one case it does not cover: product types are a small closed set an administrator curates by
 * hand (RF-13), the contract declares a flat array precisely so the SPA can cache it whole, and a
 * paginated dropdown would be a worse answer to a problem this table does not have. If it ever grows
 * past a screenful, that is a change to the contract, not a filter added here.
 */
public interface ProductTypeRepository extends JpaRepository<ProductType, Long> {}
