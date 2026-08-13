package dev.sandoretti.ukun.api.catalog.application;

import dev.sandoretti.ukun.api.catalog.domain.ProductType;
import dev.sandoretti.ukun.api.catalog.infra.ProductRepository;
import dev.sandoretti.ukun.api.catalog.infra.ProductTypeRepository;
import dev.sandoretti.ukun.api.shared.error.DuplicateValueException;
import dev.sandoretti.ukun.api.shared.error.ResourceNotFoundException;
import dev.sandoretti.ukun.api.shared.observability.BusinessEvents;
import dev.sandoretti.ukun.api.shared.observability.CurrentActor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.event.Level;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The back-office CRUD of product types (RF-13) — the first screen an administrator needs on a fresh
 * database, because creating a product means choosing one of these.
 *
 * <p>The requirement does not come from the legacy system: there, {@code tipo_producto} is seeded once
 * and adding a category means an {@code INSERT} by hand against the database. This closes that gap
 * rather than keeping parity with it.
 *
 * <p>A service of its own, next to {@link ProductTypeQueryService} rather than merged into it. The read
 * that fills the public catalogue's filter and the CRUD an administrator drives are two different
 * reads of the same table (ADR-0020), with different authorisation and different shapes; what they
 * share is the module that owns the table, which is not a reason to share a class.
 *
 * <p>{@code @Transactional(readOnly = true)} at class level with the writes opting in, per the skill
 * {@code spring-api}, and a constructor that does nothing but assign (rule 6 — the legacy has two
 * services that query in theirs and fail to start if a row is missing).
 */
@Service
@Transactional(readOnly = true)
public class ProductTypeAdminService {

    /**
     * The {@code resource} extension, spelled as the contract spells it — {@code resource: productType}
     * appears verbatim in three of its responses. Not the Java class name, which is an implementation
     * detail no client sees, and lower camel where the older {@code RESOURCE_NOT_FOUND}s of this service
     * ({@code "Product"}, {@code "Order"}) happen to be capitalised: those predate the contract naming
     * a value, and rewriting them is a change to responses this slice was not asked to touch.
     */
    private static final String PRODUCT_TYPE = "productType";

    private static final String NAME = "name";

    private static final String CREATED_EVENT = "product_type.created";
    private static final String UPDATED_EVENT = "product_type.updated";
    private static final String DELETED_EVENT = "product_type.deleted";
    private static final String DELETION_REJECTED_EVENT = "product_type.deletion.rejected";

    private final ProductTypeRepository types;
    private final ProductRepository products;
    private final ProductTypeMapper mapper;

    ProductTypeAdminService(ProductTypeRepository types, ProductRepository products, ProductTypeMapper mapper) {
        this.types = types;
        this.products = products;
        this.mapper = mapper;
    }

    /** Every type with its product count, in one statement — see {@code findAllWithProductCount}. */
    public List<AdminProductTypeResponse> list() {
        return types.findAllWithProductCount().stream().map(mapper::toAdminResponse).toList();
    }

    /** What the edit form reads. */
    public AdminProductTypeResponse get(Long id) {
        return withCount(require(id));
    }

    /**
     * Creates a type, which is born with {@code productCount: 0} by definition.
     *
     * @throws DuplicateValueException if another type already carries that name
     */
    @Transactional
    public AdminProductTypeResponse create(ProductTypeRequest request) {
        String name = normalised(request);

        if (types.existsByName(name)) {
            throw new DuplicateValueException(PRODUCT_TYPE, NAME, name);
        }

        ProductType type = new ProductType();
        type.setName(name);
        ProductType saved = types.save(type);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("productTypeId", saved.getId());
        fields.put(NAME, saved.getName());
        fields.put("adminId", CurrentActor.accountId());
        BusinessEvents.emit(Level.INFO, CREATED_EVENT, fields);

        return mapper.toAdminResponse(saved, 0);
    }

    /**
     * Renames a type. The name is its only editable field.
     *
     * <p>It drags nothing behind it: what an order line freezes is the <em>product</em> name, not its
     * type ({@code docs/10-modelo-de-datos.md} §5, ADR-0011), so no past order changes. The public
     * catalogue's filter does change, immediately and for every product of that type, which is what the
     * event records — {@code product_type.updated} is the only business event of this service that logs
     * the previous value of a free-text field, because without it the line cannot be read back into
     * what happened ({@code docs/11-observabilidad-y-logging.md} §3).
     *
     * @throws ResourceNotFoundException if no type carries that identifier
     * @throws DuplicateValueException if another type already carries the new name
     */
    @Transactional
    public AdminProductTypeResponse rename(Long id, ProductTypeRequest request) {
        ProductType type = require(id);
        String name = normalised(request);
        String previousName = type.getName();

        if (types.existsByNameAndIdNot(name, id)) {
            throw new DuplicateValueException(PRODUCT_TYPE, NAME, name);
        }

        type.setName(name);
        ProductType saved = types.save(type);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("productTypeId", saved.getId());
        fields.put("previousName", previousName);
        fields.put("newName", saved.getName());
        fields.put("adminId", CurrentActor.accountId());
        BusinessEvents.emit(Level.INFO, UPDATED_EVENT, fields);

        return withCount(saved);
    }

    /**
     * Deletes a type, which is a real {@code DELETE} and has no logical variant.
     *
     * <p>That is possible precisely because a type with no products is referenced by nothing, so there
     * is no state it can get stuck in — the difference with {@code product}, where a sold one can never
     * be deleted and therefore needs discontinuation as an escape (ADR-0013).
     *
     * <p>The count runs first, and it does <strong>not</strong> filter by {@code is_active}: a type
     * whose products are all discontinued is still a type in use, because the foreign key does not care
     * either. The {@code RESTRICT} on {@code product.product_type_id} is the guarantee — it survives a
     * {@code DELETE} typed straight into {@code psql} — and this check is the experience, since a raw
     * {@code 23503} cannot say how many.
     *
     * @throws ResourceNotFoundException if no type carries that identifier
     * @throws ProductTypeInUseException if any product still points at it
     */
    @Transactional
    public void delete(Long id) {
        ProductType type = require(id);
        long inProducts = products.countByProductTypeId(id);

        if (inProducts > 0) {
            Map<String, Object> rejected = new LinkedHashMap<>();
            rejected.put("productTypeId", id);
            rejected.put("inProducts", inProducts);
            BusinessEvents.emit(Level.INFO, DELETION_REJECTED_EVENT, rejected);

            throw new ProductTypeInUseException(id, inProducts);
        }

        types.delete(type);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("productTypeId", id);
        fields.put(NAME, type.getName());
        fields.put("adminId", CurrentActor.accountId());
        BusinessEvents.emit(Level.WARN, DELETED_EVENT, fields);
    }

    /**
     * Trimmed here rather than in the request record, because trimming is part of deciding whether the
     * name clashes: {@code " Sofás"} and {@code "Sofás"} are the same category to a person, and storing
     * the first would sit next to the second in the dropdown looking like a duplicate the unique index
     * had failed to catch. The contract says as much ("trimmed, never blank").
     */
    private static String normalised(ProductTypeRequest request) {
        return request.name().trim();
    }

    private ProductType require(Long id) {
        return types.findById(id).orElseThrow(() -> new ResourceNotFoundException(PRODUCT_TYPE, id));
    }

    /**
     * The count for a single type, which the listing gets in its own grouped query and a single read
     * has to ask for. One extra statement on a screen that shows one row, and the alternative — running
     * the grouped query and picking one row out of it — would read the whole table to answer about one.
     */
    private AdminProductTypeResponse withCount(ProductType type) {
        return mapper.toAdminResponse(type, products.countByProductTypeId(type.getId()));
    }
}
