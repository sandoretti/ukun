package dev.sandoretti.ukun.api.catalog.application;

import dev.sandoretti.ukun.api.catalog.ProductReferenceKind;
import dev.sandoretti.ukun.api.catalog.domain.Product;
import dev.sandoretti.ukun.api.catalog.domain.ProductType;
import dev.sandoretti.ukun.api.catalog.infra.ProductRepository;
import dev.sandoretti.ukun.api.catalog.infra.ProductSpecifications;
import dev.sandoretti.ukun.api.catalog.infra.ProductTypeRepository;
import dev.sandoretti.ukun.api.shared.error.DuplicateValueException;
import dev.sandoretti.ukun.api.shared.error.ResourceNotFoundException;
import dev.sandoretti.ukun.api.shared.observability.BusinessEvents;
import dev.sandoretti.ukun.api.shared.observability.CurrentActor;
import dev.sandoretti.ukun.api.shared.web.PageResponse;
import dev.sandoretti.ukun.api.shared.web.PaginationParams;
import dev.sandoretti.ukun.api.storage.FileStoragePort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.event.Level;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The back-office CRUD of products (RF-10): the listing that sees withdrawn products, the writes, and
 * the two operations that take a product out of the catalogue and put it back.
 *
 * <p><strong>The catalogue is global.</strong> No operation here is scoped by a store, which is the
 * difference with stock (RF-09) and the reason none of these methods resolves the admin's own store:
 * authorisation is by role, decided by {@code @PreAuthorize} before the method runs, and there is no
 * resource ownership to check ({@code docs/08-identidad-y-seguridad.md} §4). The actor is still named
 * in every event, because "who withdrew this product" is a question a shop asks.
 *
 * <p>Separate from {@link ProductQueryService} for the reason ADR-0020 gives: these are two reads of
 * one table with different audiences — the public one anonymous and cacheable, this one neither — plus
 * a write side the public service must never grow.
 *
 * <p>Editing is <strong>last write wins</strong>: no {@code @Version}, no {@code If-Match}, decided in
 * {@code docs/10-modelo-de-datos.md} §2. Two admins editing one product is a collision between people
 * who work together, unlike the two customers checking out the same unit that ADR-0008 arbitrates.
 */
@Service
@Transactional(readOnly = true)
public class ProductAdminService {

    /** The contract's names for these resources — see the note in {@link ProductTypeAdminService}. */
    private static final String PRODUCT = "product";

    private static final String PRODUCT_TYPE = "productType";

    private static final String SKU = "sku";
    private static final String PRODUCT_ID = "productId";
    private static final String ADMIN_ID = "adminId";

    private static final String CREATED_EVENT = "product.created";
    private static final String UPDATED_EVENT = "product.updated";
    private static final String DELETED_EVENT = "product.deleted";
    private static final String DELETION_REJECTED_EVENT = "product.deletion.rejected";
    private static final String DISCONTINUED_EVENT = "product.discontinued";
    private static final String REACTIVATED_EVENT = "product.reactivated";

    private final ProductRepository products;
    private final ProductTypeRepository types;
    private final ProductReferences references;
    private final FileStoragePort storage;
    private final ProductMapper mapper;

    ProductAdminService(
            ProductRepository products,
            ProductTypeRepository types,
            ProductReferences references,
            FileStoragePort storage,
            ProductMapper mapper) {

        this.products = products;
        this.types = types;
        this.references = references;
        this.storage = storage;
        this.mapper = mapper;
    }

    /**
     * The back-office listing (RF-10). Unlike the public one it can show discontinued products, which is
     * what makes reactivation possible at all.
     *
     * <p>It composes the very same {@link ProductSpecifications} the shop's listing does, and reuses the
     * repository's {@code @EntityGraph} with it: {@code typeName} is part of every row here too, so
     * without the graph this screen would fetch one type per product — the N+1 of defect #12, in a
     * second place.
     */
    public PageResponse<AdminProductResponse> list(AdminProductFilters filters, PaginationParams pagination) {
        Page<Product> page =
                products.findAll(specificationFor(filters), pagination.toPageable(filters.sort().toSort()));

        return PageResponse.of(page, mapper::toAdminResponse);
    }

    /**
     * What the edit form reads. It resolves discontinued products, which is exactly what the public
     * {@code GET /api/products/{id}} does not do.
     */
    public AdminProductResponse detail(Long id) {
        return mapper.toAdminResponse(require(id));
    }

    /**
     * Creates a product, active and without a photo. The image is uploaded afterwards against its own
     * sub-resource.
     *
     * @throws ResourceNotFoundException if {@code typeId} matches no product type
     * @throws DuplicateValueException if the SKU already belongs to another product, discontinued or not
     */
    @Transactional
    public AdminProductResponse create(CreateProductRequest request) {
        if (products.existsBySku(request.sku())) {
            throw new DuplicateValueException(PRODUCT, SKU, request.sku());
        }

        Product product = new Product();
        product.setSku(request.sku());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setProductType(typeOf(request.typeId()));

        Product saved = products.save(product);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(PRODUCT_ID, saved.getId());
        fields.put("name", saved.getName());
        fields.put(ADMIN_ID, CurrentActor.accountId());
        BusinessEvents.emit(Level.INFO, CREATED_EVENT, fields);

        return mapper.toAdminResponse(saved);
    }

    /**
     * Edits name, description, price and type. Not the SKU, not {@code isActive}, not the image — see
     * {@link UpdateProductRequest} for why each of the three is absent.
     *
     * <p>The event records <em>which</em> fields changed and not their values
     * ({@code docs/11-observabilidad-y-logging.md} §3). A price is not a secret, but a log line that
     * repeats every edited value grows into a second copy of the table, and the question the event
     * answers is "what was touched".
     *
     * @throws ResourceNotFoundException if the product, or the type it is being moved to, does not exist
     */
    @Transactional
    public AdminProductResponse update(Long id, UpdateProductRequest request) {
        Product product = require(id);
        List<String> changed = new ArrayList<>();

        if (!product.getName().equals(request.name())) {
            product.setName(request.name());
            changed.add("name");
        }
        if (!product.getDescription().equals(request.description())) {
            product.setDescription(request.description());
            changed.add("description");
        }
        if (product.getPrice().compareTo(request.price()) != 0) {
            product.setPrice(request.price());
            changed.add("price");
        }
        if (!product.getProductType().getId().equals(request.typeId())) {
            product.setProductType(typeOf(request.typeId()));
            changed.add("typeId");
        }

        Product saved = products.save(product);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(PRODUCT_ID, saved.getId());
        fields.put("changedFields", String.join(",", changed));
        BusinessEvents.emit(Level.INFO, UPDATED_EVENT, fields);

        return mapper.toAdminResponse(saved);
    }

    /**
     * A hard delete, possible only for a product nothing has ever referenced.
     *
     * <p>The counts come first so the {@code 409} can say where the references are, instead of letting
     * Postgres' {@code 23503} reach the user (ADR-0013). The {@code RESTRICT} on every inbound foreign
     * key remains the guarantee: it survives this check being wrong, and it closes the race between
     * counting and deleting.
     *
     * <p>The image goes with the product, and it goes <em>after</em> the row is gone: if the delete were
     * to fail, the transaction rolls back and a file deleted first would have left a live product
     * pointing at nothing. This is the same ordering rule the image replacement follows, applied to the
     * end of a product's life.
     *
     * @throws ResourceNotFoundException if no product carries that identifier
     * @throws ProductInUseException if an order line, a stock row or a cart line points at it
     */
    @Transactional
    public void delete(Long id) {
        Product product = require(id);
        Map<ProductReferenceKind, Long> counts = references.countFor(id);

        if (counts.values().stream().anyMatch(count -> count > 0)) {
            Map<String, Object> rejected = new LinkedHashMap<>();
            rejected.put(PRODUCT_ID, id);
            counts.forEach((kind, count) -> rejected.put(kind.extension(), count));
            BusinessEvents.emit(Level.INFO, DELETION_REJECTED_EVENT, rejected);

            throw new ProductInUseException(id, counts);
        }

        String imageKey = product.getImageKey();
        products.delete(product);

        // Flushed here so that a reference created between the count and this statement surfaces as a
        // DataIntegrityViolationException the advice can still render. At commit time it would be
        // outside every handler and would reach the client as a 500 — the same reason StockService
        // flushes its optimistic write explicitly.
        products.flush();

        if (imageKey != null) {
            storage.delete(imageKey);
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(PRODUCT_ID, id);
        fields.put("name", product.getName());
        fields.put(ADMIN_ID, CurrentActor.accountId());
        BusinessEvents.emit(Level.WARN, DELETED_EVENT, fields);
    }

    /**
     * Takes the product out of the public catalogue (RF-01) and out of the stock screens (RF-09),
     * leaving order history untouched and readable — the line keeps its own name and price snapshot
     * (ADR-0011).
     *
     * <p>An operation of its own and not a field of the edit form, because it is a business exit rather
     * than a value: it has its own event and its own audit trail (ADR-0013, ADR-0014). Burying a
     * catalogue-wide decision inside a price change is how it stops being visible.
     *
     * <p>Idempotent: discontinuing an already discontinued product answers {@code 200} and changes
     * nothing — including the event, which is not emitted, because nothing happened to record.
     */
    @Transactional
    public AdminProductResponse discontinue(Long id) {
        return setActive(id, false, DISCONTINUED_EVENT);
    }

    /**
     * The exact inverse, and the reason the listing has to be able to show discontinued products at all.
     *
     * <p>Availability is not affected: a reactivated product with no stock is back in the catalogue and
     * unavailable, which are two different facts (RF-01, ADR-0017 §3).
     */
    @Transactional
    public AdminProductResponse reactivate(Long id) {
        return setActive(id, true, REACTIVATED_EVENT);
    }

    /**
     * The shared half of the two idempotent operations. The event is emitted only on a real change, so
     * that a screen refreshed twice does not read as two withdrawals in the audit trail.
     */
    private AdminProductResponse setActive(Long id, boolean active, String event) {
        Product product = require(id);

        if (product.isActive() == active) {
            return mapper.toAdminResponse(product);
        }

        product.setActive(active);
        Product saved = products.save(product);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(PRODUCT_ID, saved.getId());
        fields.put(SKU, saved.getSku());
        fields.put(ADMIN_ID, CurrentActor.accountId());
        BusinessEvents.emit(Level.INFO, event, fields);

        return mapper.toAdminResponse(saved);
    }

    /**
     * The product by identifier, active or not — which is what separates every read of this service from
     * the public ones, where a discontinued product is a {@code 404}.
     */
    private Product require(Long id) {
        return products.findById(id).orElseThrow(() -> new ResourceNotFoundException(PRODUCT, id));
    }

    /**
     * The type a write points at, resolved rather than trusted.
     *
     * <p>A {@code getReferenceById} would defer the check to the foreign key and surface it as a
     * {@code 23503} the safety net turns into an anonymous {@code REFERENTIAL_INTEGRITY}; the contract
     * asks for a {@code 404} naming {@code productType}, which needs the row to have been looked for.
     */
    private ProductType typeOf(Long typeId) {
        return types.findById(typeId).orElseThrow(() -> new ResourceNotFoundException(PRODUCT_TYPE, typeId));
    }

    /**
     * {@code status} is the only condition that is always present, and even it contributes nothing for
     * {@code ALL}. The rest join the list when the request carried them — the same composition the
     * public listing makes, which is what keeps one paginated method enough.
     */
    private static Specification<Product> specificationFor(AdminProductFilters filters) {
        List<Specification<Product>> conditions = new ArrayList<>();
        filters.status().toSpecification().ifPresent(conditions::add);

        if (filters.q() != null) {
            conditions.add(ProductSpecifications.nameContains(filters.q()));
        }
        if (filters.sku() != null) {
            conditions.add(ProductSpecifications.hasSku(filters.sku()));
        }
        if (filters.typeId() != null) {
            conditions.add(ProductSpecifications.hasType(filters.typeId()));
        }

        return Specification.allOf(conditions);
    }
}
