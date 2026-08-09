package dev.sandoretti.ukun.api.stores.infra;

import dev.sandoretti.ukun.api.stores.domain.Store;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * {@code store}, the table the {@code stores} module owns, plus the {@code address} row hanging off
 * it.
 *
 * <p>The {@code @EntityGraph} on the inherited {@code findAll} is what keeps the listing free of the
 * N+1 of defect #12: every row of the response carries the full address, and without the graph the
 * lazy association would fire one {@code SELECT} per store. ADR-0017 §2 lists "stores + address" among
 * the four N+1-prone reads and notes it stays intra-module precisely so the graph keeps working.
 *
 * <p>An unbounded listing looks like a breach of rule 8 of the skill {@code spring-api} and is the
 * same exception {@code ProductTypeRepository} documents: the contract declares a flat array because
 * this is closed reference data — four rows today, a handful ever — and paginating a store picker
 * would be a worse answer to a problem this table does not have. If it ever grows past a screenful,
 * that is a change to the contract, not a filter added here.
 */
public interface StoreRepository extends JpaRepository<Store, Long>, JpaSpecificationExecutor<Store> {

    @Override
    @EntityGraph(attributePaths = "address")
    List<Store> findAll(Specification<Store> specification, Sort sort);
}
