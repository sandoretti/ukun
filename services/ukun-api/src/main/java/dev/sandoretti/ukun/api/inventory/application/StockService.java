package dev.sandoretti.ukun.api.inventory.application;

import dev.sandoretti.ukun.api.audit.AuditTrail;
import dev.sandoretti.ukun.api.audit.AuditedEvent;
import dev.sandoretti.ukun.api.catalog.CatalogApi;
import dev.sandoretti.ukun.api.catalog.ProductSummary;
import dev.sandoretti.ukun.api.inventory.domain.StoreStock;
import dev.sandoretti.ukun.api.inventory.infra.StoreStockRepository;
import dev.sandoretti.ukun.api.shared.error.ResourceNotFoundException;
import dev.sandoretti.ukun.api.shared.error.VersionConflictException;
import dev.sandoretti.ukun.api.shared.observability.BusinessEvents;
import dev.sandoretti.ukun.api.shared.persistence.TextSearch;
import dev.sandoretti.ukun.api.shared.web.PageResponse;
import dev.sandoretti.ukun.api.shared.web.PaginationParams;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.event.Level;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The four things an employee does with the stock of their own store (RF-09): read it, add a line by
 * SKU, change how many units a line holds, and remove the line.
 *
 * <p><strong>Every one of them starts by resolving who is asking.</strong> The {@code storeId} that
 * arrives in the path is passed to {@link EmployeeStoreResolver} to be <em>checked</em>, never to be
 * used; what the rest of the method works with is the store that came back from the database
 * ({@code docs/08-identidad-y-seguridad.md} §4). The legacy compares a {@code tiendaId} query
 * parameter against the session by hand in one of its four handlers, which is the defect this shape is
 * built to make unwritable.
 *
 * <p><strong>Concurrency here is optimistic, on purpose and by contrast.</strong> Two employees
 * editing the same line is a rare collision between people who can talk to each other, and the right
 * answer is to tell the loser to look again ({@code 409 VERSION_CONFLICT}). The pessimistic lock of
 * ADR-0008 is for the other write on this table — checkout — where the collision is between customers
 * who cannot, and where losing it means overselling. Same table, two strategies, both deliberate.
 */
@Service
@Transactional(readOnly = true)
public class StockService {

    private static final String STOCK_LINE = "StockLine";
    private static final String PRODUCT = "Product";

    /**
     * The one event of this service that leaves no audit row: a collision changed nothing, and what it
     * measures is how often two employees work on the same line ({@code docs/11} §3). The other two go
     * through {@link AuditedEvent}, which owns their names.
     */
    private static final String CONFLICT_EVENT = "stock.optimistic.conflict";

    private static final String PRODUCT_ID = "productId";
    private static final String STORE_ID = "storeId";
    private static final String EMPLOYEE_ID = "employeeId";

    private final StoreStockRepository stock;
    private final EmployeeStoreResolver employees;
    private final CatalogApi catalog;
    private final StockMapper mapper;
    private final AuditTrail auditTrail;

    StockService(
            StoreStockRepository stock,
            EmployeeStoreResolver employees,
            CatalogApi catalog,
            StockMapper mapper,
            AuditTrail auditTrail) {

        this.stock = stock;
        this.employees = employees;
        this.catalog = catalog;
        this.mapper = mapper;
        this.auditTrail = auditTrail;
    }

    /**
     * Either half of the stock screen, paginated and filtered (query Q5).
     *
     * <p>The {@code Pageable} carries no {@code Sort}: both queries are native and order themselves,
     * because the columns they sort by belong to {@code product} and the ordering has to be inside the
     * statement that pages. Handing a sort to a native query is rejected by Spring Data, which is the
     * loud version of the same fact.
     */
    public PageResponse<StockLineResponse> list(
            Long storeId, StockStatus status, String sku, String name, PaginationParams pagination) {

        CurrentEmployee employee = employees.requireEmployeeOf(storeId);
        Pageable pageable = PageRequest.of(pagination.page(), pagination.size());
        String namePattern = name == null ? null : TextSearch.likePattern(name);

        Page<StoreStockRepository.StockLineView> page = status == StockStatus.NOT_STOCKED
                ? stock.pageOfNotStockedProducts(employee.storeId(), sku, namePattern, pageable)
                : stock.pageOfStockedLines(employee.storeId(), sku, namePattern, pageable);

        return PageResponse.of(page, mapper::toResponse);
    }

    /**
     * Adds a line for a product this store does not stock yet.
     *
     * <p>The SKU resolves through {@code CatalogApi}, which only answers for active products — so a
     * discontinued one is a {@code 404} here and cannot be restocked (ADR-0013). The duplicate check
     * comes second because it needs the identifier the first step produced.
     *
     * @throws ResourceNotFoundException if no active product carries that SKU
     * @throws StockLineAlreadyExistsException if the product already has a line in this store
     */
    @Transactional
    public StockLineResponse create(Long storeId, CreateStockLineRequest request) {
        CurrentEmployee employee = employees.requireEmployeeOf(storeId);

        ProductSummary product = catalog.activeBySku(request.sku())
                .orElseThrow(() -> new ResourceNotFoundException(PRODUCT, request.sku()));

        if (stock.existsByProductIdAndStoreId(product.id(), employee.storeId())) {
            throw new StockLineAlreadyExistsException(request.sku());
        }

        StoreStock line = new StoreStock();
        line.setProductId(product.id());
        line.setStoreId(employee.storeId());
        line.setQuantity(request.quantity());

        StoreStock saved = stock.save(line);
        emitUpdated(saved, employee, 0);

        return mapper.toResponse(saved, product);
    }

    /**
     * Changes how many units a line holds, if nobody else changed it first.
     *
     * <p>The version is checked twice, and the two checks catch different things. Comparing the value
     * the client sent against the one on the row catches the stale form — the employee read the line,
     * went for coffee, and somebody else edited it meanwhile. The {@code saveAndFlush} catches the
     * narrow race inside this transaction, where the other write lands between this read and this
     * write; without the explicit flush that failure would surface at commit time, outside any handler,
     * and reach the client as a {@code 500}.
     *
     * @throws ResourceNotFoundException if the line does not exist, or belongs to another store
     * @throws VersionConflictException if the line was modified since the client read it
     */
    @Transactional
    public StockLineResponse update(Long storeId, Long lineId, UpdateStockLineRequest request) {
        CurrentEmployee employee = employees.requireEmployeeOf(storeId);
        StoreStock line = lineOf(employee, lineId);

        if (line.getVersion() != request.version()) {
            throw conflict(line, employee);
        }

        int before = line.getQuantity();
        line.setQuantity(request.quantity());

        StoreStock saved;
        try {
            saved = stock.saveAndFlush(line);
        } catch (OptimisticLockingFailureException failure) {
            throw conflict(line, employee);
        }
        emitUpdated(saved, employee, before);

        return mapper.toResponse(saved, productOf(saved));
    }

    /**
     * Removes the line: this store stops managing that product at all.
     *
     * <p><strong>Not the same as setting the quantity to zero</strong>, which the contract says in as
     * many words. A line at zero is a product the store keeps and has run out of; no line is a product
     * the store does not carry. Collapsing the two would leave the "add new stock" screen unable to
     * offer anything back.
     *
     * <p>It takes no {@code version}. Deleting is not a lost update: whoever else was editing the line
     * wanted it to exist, and their write failing loudly afterwards is the correct outcome rather than
     * something to arbitrate here.
     *
     * @throws ResourceNotFoundException if the line does not exist, or belongs to another store
     */
    @Transactional
    public void delete(Long storeId, Long lineId) {
        CurrentEmployee employee = employees.requireEmployeeOf(storeId);
        StoreStock line = lineOf(employee, lineId);

        stock.delete(line);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(PRODUCT_ID, line.getProductId());
        fields.put(STORE_ID, line.getStoreId());
        fields.put("previousQuantity", line.getQuantity());
        fields.put(EMPLOYEE_ID, employee.employeeId());

        // The line and not the product: ADR-0015 gave it its own identity and its own URL, so it is
        // what the trail addresses. Two lines of the same product in two stores are two resources.
        auditTrail.record(AuditedEvent.STOCK_DELETED, line.getId(), fields);
    }

    /**
     * The line, looked up together with the store so that somebody else's line is a {@code 404} and not
     * a {@code 403} — see {@link StoreStockRepository#findByIdAndStoreId}.
     */
    private StoreStock lineOf(CurrentEmployee employee, Long lineId) {
        return stock.findByIdAndStoreId(lineId, employee.storeId())
                .orElseThrow(() -> new ResourceNotFoundException(STOCK_LINE, lineId));
    }

    /**
     * The product a written line points at, for the response.
     *
     * <p>Through {@code summariesByIds} even for a single identifier, because it is the one way this
     * module is allowed to read the catalogue ({@code docs/14-modules.md} §3) and there is no per-row
     * variant to reach for by mistake. One line written, one product read.
     *
     * @throws IllegalStateException if the product is gone. The line exists and {@code product_id} is a
     *     foreign key, so this is a broken invariant of the service rather than a client error.
     */
    private ProductSummary productOf(StoreStock line) {
        Map<Long, ProductSummary> products = catalog.summariesByIds(Set.of(line.getProductId()));
        ProductSummary product = products.get(line.getProductId());

        if (product == null) {
            throw new IllegalStateException("Stock line " + line.getId() + " points at a product that is gone");
        }
        return product;
    }

    /** {@code stock.updated} — the same event for a line created and a line edited ({@code docs/11} §3). */
    private void emitUpdated(StoreStock line, CurrentEmployee employee, int before) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(PRODUCT_ID, line.getProductId());
        fields.put(STORE_ID, line.getStoreId());
        fields.put("before", before);
        fields.put("after", line.getQuantity());
        fields.put(EMPLOYEE_ID, employee.employeeId());

        auditTrail.record(AuditedEvent.STOCK_UPDATED, line.getId(), fields);
    }

    /**
     * {@code stock.optimistic.conflict} plus the exception that carries it to the client.
     *
     * <p>The event is emitted from both paths that detect the collision, because what it measures is
     * how often two employees work on the same line — a fact about the shop, not about which of the two
     * checks noticed.
     */
    private VersionConflictException conflict(StoreStock line, CurrentEmployee employee) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(PRODUCT_ID, line.getProductId());
        fields.put(STORE_ID, line.getStoreId());
        fields.put(EMPLOYEE_ID, employee.employeeId());

        BusinessEvents.emit(Level.WARN, CONFLICT_EVENT, fields);

        return new VersionConflictException(STOCK_LINE, line.getId());
    }
}
