package dev.sandoretti.ukun.api.catalog.infra;

import dev.sandoretti.ukun.api.catalog.domain.ProductType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * {@code product_type}, the other table the {@code catalog} module owns.
 *
 * <p>The public read is the inherited {@code findAll(Sort)}, with the sort coming from the use case
 * rather than from a derived query name so that the tie-breaker of defect #23 is declared in one
 * place. What RF-13 adds on top are the two uniqueness checks of the write side and the counted
 * listing of the back-office.
 *
 * <p>An unbounded {@code findAll} looks like a breach of rule 8 of the skill {@code spring-api} and is
 * the one case it does not cover: product types are a small closed set an administrator curates by
 * hand (RF-13), the contract declares a flat array precisely so the SPA can cache it whole, and a
 * paginated dropdown would be a worse answer to a problem this table does not have. If it ever grows
 * past a screenful, that is a change to the contract, not a filter added here.
 */
public interface ProductTypeRepository extends JpaRepository<ProductType, Long> {

    /**
     * The uniqueness of {@code ux_product_type_name}, asked before writing so the answer can name the
     * field (RF-13, {@code DUPLICATE_VALUE}).
     *
     * <p>Exact match, and deliberately not {@code existsByNameIgnoreCase}: the unique index is on the
     * column as it is, so a case-insensitive check here would reject a name the database would have
     * accepted — the API forbidding what the schema allows, which is a divergence and not extra care.
     * Making the index case-insensitive is a schema decision, and it would be a changeset.
     */
    boolean existsByName(String name);

    /** The same check for a rename, where the type's own row is not a clash with itself. */
    boolean existsByNameAndIdNot(String name, Long id);

    /**
     * The back-office listing of RF-13: every type with how many products hang off it.
     *
     * <p><strong>One statement, not one plus a count per row.</strong> The obvious shape — list the
     * types, then ask {@code countByProductTypeId} for each — is the N+1 of defect #12 in its arithmetic
     * disguise, and it grows with the number of categories the shop invents. The grouped query is the
     * third rung of the reading ladder of {@code docs/03-analisis-y-patrones.md} §2: a projection, because
     * what the response needs is a number that no entity holds.
     *
     * <p>{@code left join} and not an inner one: a type with no products is the normal case right after
     * creating one, and it is precisely the type that <em>can</em> be deleted. An inner join would hide
     * exactly the rows the screen is most able to act on.
     *
     * <p>The count is over {@code p.id}, so an empty type counts zero rather than one — {@code count(p)}
     * over a null row of a left join is the classic off-by-one here. It does not filter by
     * {@code is_active}, which ADR-0013 requires: the foreign key does not either.
     *
     * <p>Ordered by name with {@code id} as the last criterion, like every other {@code Sort} of this
     * API (defect #23).
     */
    @Query(
            """
            select t.id as id,
                   t.name as name,
                   count(p.id) as productCount,
                   t.createdAt as createdAt,
                   t.updatedAt as updatedAt
              from ProductType t
              left join Product p on p.productType = t
             group by t.id, t.name, t.createdAt, t.updatedAt
             order by t.name asc, t.id asc
            """)
    List<AdminProductTypeView> findAllWithProductCount();

    /** One row of the back-office listing: the type, plus the number that decides whether it is deletable. */
    interface AdminProductTypeView {

        Long getId();

        String getName();

        long getProductCount();

        Instant getCreatedAt();

        Instant getUpdatedAt();
    }
}
