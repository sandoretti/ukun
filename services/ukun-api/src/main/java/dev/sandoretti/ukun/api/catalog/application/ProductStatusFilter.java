package dev.sandoretti.ukun.api.catalog.application;

import dev.sandoretti.ukun.api.catalog.domain.Product;
import dev.sandoretti.ukun.api.catalog.infra.ProductSpecifications;
import java.util.Optional;
import org.springframework.data.jpa.domain.Specification;

/**
 * Which products the back-office listing returns (RF-10, ADR-0013).
 *
 * <p>The default is {@code ACTIVE}, matching the public catalogue: the commonest screen and the
 * commonest read agree on what "the catalogue" means, and seeing withdrawn products is something the
 * admin asks for.
 *
 * <p>{@code ALL} maps to no condition at all rather than to a predicate that is always true. That is
 * the difference between a filter the query does not carry and one it carries and the database has to
 * evaluate — and it keeps the composition in {@code ProductAdminService} reading as a list of the
 * conditions the request actually asked for.
 */
public enum ProductStatusFilter {
    ACTIVE,
    DISCONTINUED,
    ALL;

    /** What the contract declares as the default of the {@code ProductStatusFilter} schema. */
    public static final ProductStatusFilter DEFAULT = ACTIVE;

    /** Empty for {@link #ALL}: nothing to add to the query. */
    public Optional<Specification<Product>> toSpecification() {
        return switch (this) {
            case ACTIVE -> Optional.of(ProductSpecifications.isActive());
            case DISCONTINUED -> Optional.of(ProductSpecifications.isDiscontinued());
            case ALL -> Optional.empty();
        };
    }
}
