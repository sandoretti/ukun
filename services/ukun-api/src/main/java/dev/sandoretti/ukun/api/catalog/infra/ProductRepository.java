package dev.sandoretti.ukun.api.catalog.infra;

import dev.sandoretti.ukun.api.catalog.domain.Product;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Query Q1 of {@code docs/10-modelo-de-datos.md}: the filtered, sorted, paginated catalog.
 *
 * <p><strong>One paginated method, not a family of derived queries.</strong> Four optional filters
 * would mean sixteen {@code findByNameAndProductTypeAndPriceBetween…} combinations; the skill
 * {@code spring-api} and {@code docs/03-analisis-y-patrones.md} §2 both settle this on a composed
 * {@link Specification} instead.
 *
 * <p>The {@code @EntityGraph} on the inherited {@code findAll} is what keeps the listing free of the
 * N+1 of defect #12: {@code typeName} is part of every row of the response, and without the graph
 * each product would fetch its type in a query of its own — the lazy association would trigger one
 * {@code SELECT} per row. It is the second rung of the reading ladder of {@code docs/03} §2, chosen
 * over a projection because the mapper needs the type entity, and over a fetch join because nothing
 * filters or sorts by the association. Spring Data leaves the {@code count} query alone, which is
 * what keeps the pair at exactly two statements.
 */
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @Override
    @EntityGraph(attributePaths = "productType")
    Page<Product> findAll(Specification<Product> specification, Pageable pageable);

    /**
     * The detail read (RF-01, {@code getProduct}). The same {@code @EntityGraph} and for the same
     * reason: {@code typeName} is part of {@code ProductDetail} too, and without the graph resolving
     * it outside the transaction would fail under {@code open-in-view: false} — or, worse, succeed
     * inside it as a second {@code SELECT} nobody asked for.
     *
     * <p>It stays {@code findById} and does not grow an {@code AndIsActiveTrue}: whether a
     * discontinued product is a {@code 404} is a rule of the public catalog (ADR-0013), and the
     * back-office read of v0.7 wants the same row by the same primary key. The rule belongs to the use
     * case, which is where it is applied and where the exception is thrown.
     */
    @Override
    @EntityGraph(attributePaths = "productType")
    Optional<Product> findById(Long id);

    /**
     * By business identifier, for {@link dev.sandoretti.ukun.api.catalog.CatalogApi#activeBySku(String)}.
     *
     * <p>No {@code @EntityGraph}, unlike the two overrides above: what this feeds is a
     * {@code ProductSummary}, which carries no product type, so fetching the association would be a
     * join nobody reads. Served by the {@code UNIQUE} on {@code sku} (ADR-0015).
     */
    Optional<Product> findBySku(String sku);
}
