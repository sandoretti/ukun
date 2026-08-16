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

    /**
     * The same row as {@link #findBySku(String)}, with the product type fetched: the detail read of
     * {@code getProductBySku} (RF-09), which returns the very same {@code ProductDetail} as
     * {@code getProduct} and therefore carries {@code typeName}.
     *
     * <p>Two derived queries over the same unique column rather than one with the graph, because the
     * two reads want different fetch plans and the difference is not cosmetic. {@link #findBySku(String)}
     * feeds a {@link dev.sandoretti.ukun.api.catalog.ProductSummary} that has no type, so giving
     * <em>it</em> the graph would add a join nobody reads to the look-up {@code inventory} performs
     * before every stock line; leaving <em>this</em> one without it would either fire a second
     * {@code SELECT} or fail outright under {@code open-in-view: false}. It is the same pair as
     * {@link #findById(Long)} against {@code findAllById}, one column over.
     *
     * <p>Everything between {@code find} and {@code By} is descriptive to Spring Data, so this is the
     * {@code sku} look-up the name says it is — served by the {@code UNIQUE} on {@code sku} (ADR-0015),
     * one indexed statement.
     */
    @EntityGraph(attributePaths = "productType")
    Optional<Product> findWithProductTypeBySku(String sku);

    /**
     * The uniqueness of {@code ux_product_sku}, asked before inserting so the {@code 409} can name the
     * field (RF-10, {@code DUPLICATE_VALUE}).
     *
     * <p>It does not filter by {@code is_active}, and that is the rule rather than an oversight: a SKU
     * identifies one product for life (ADR-0015), so reusing the code of a discontinued product is
     * exactly the case this refuses. The physical label on the shelf does not know the product was
     * withdrawn.
     */
    boolean existsBySku(String sku);

    /**
     * How many products point at a type, for the {@code 409 PRODUCT_TYPE_IN_USE} of RF-13.
     *
     * <p>Served by {@code ix_product_type} ({@code docs/10-modelo-de-datos.md} §4) and deliberately
     * unfiltered by {@code is_active}: the foreign key that blocks the delete does not care whether the
     * products are discontinued, so a count that did would promise a deletion the database refuses
     * (ADR-0013).
     */
    long countByProductTypeId(Long productTypeId);
}
