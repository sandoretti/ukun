package dev.sandoretti.ukun.api.catalog.application;

import dev.sandoretti.ukun.api.catalog.infra.ProductTypeRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read side of {@code product_type} (RF-01, {@code listProductTypes} of the contract) — the list
 * that fills the type filter of the catalog.
 *
 * <p>A service of its own rather than one more method on {@link ProductQueryService}, and the same
 * goes for its repository, its mapper and its controller: {@code product_type} is a separate aggregate
 * with a separate lifecycle, and the only thing it shares with {@code product} is the module that owns
 * both tables. Nothing in this read path touches anything on the product's.
 *
 * <p>{@code @Transactional(readOnly = true)} at class level and an empty constructor, for the same
 * two reasons as every other query service here: {@code open-in-view: false} makes a lazy access
 * outside the transaction fail loudly instead of degrading into an N+1, and rule 6 of the skill
 * {@code spring-api} keeps data look-ups out of bean construction — the legacy has two services that
 * do not start if a single row is missing.
 */
@Service
@Transactional(readOnly = true)
public class ProductTypeQueryService {

    /**
     * Alphabetical, as the contract's {@code 200} says ("Product types, sorted by name"), with
     * {@code id} appended as the last criterion.
     *
     * <p>The tie-breaker is not ceremony on a column that happens to be unique in the database today:
     * defect #23 is the rule that <em>every</em> {@code Sort} of this API is total, and a rule with an
     * exception for the cases someone judged safe is a rule that gets forgotten in the case that was
     * not. It costs nothing here and it is one less thing to think about.
     */
    private static final Sort BY_NAME = Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.ASC, "id"));

    private final ProductTypeRepository types;
    private final ProductTypeMapper mapper;

    public ProductTypeQueryService(ProductTypeRepository types, ProductTypeMapper mapper) {
        this.types = types;
        this.mapper = mapper;
    }

    public List<ProductTypeResponse> list() {
        return types.findAll(BY_NAME).stream().map(mapper::toResponse).toList();
    }
}
