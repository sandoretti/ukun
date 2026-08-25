package dev.sandoretti.ukun.api.staff.application;

import dev.sandoretti.ukun.api.identity.AccountRegistrationService;
import dev.sandoretti.ukun.api.identity.CreateAccountCommand;
import dev.sandoretti.ukun.api.identity.EmailAlreadyExistsException;
import dev.sandoretti.ukun.api.identity.Registration;
import dev.sandoretti.ukun.api.identity.Role;
import dev.sandoretti.ukun.api.shared.web.PageResponse;
import dev.sandoretti.ukun.api.shared.web.PaginationParams;
import dev.sandoretti.ukun.api.staff.domain.Employee;
import dev.sandoretti.ukun.api.staff.infra.EmployeeRepository;
import dev.sandoretti.ukun.api.staff.infra.EmployeeSpecifications;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The back-office management of employees (RF-11): listing the staff of the admin's store, and adding
 * somebody to it.
 *
 * <p><strong>The store is never a parameter of anything here.</strong> Both operations open by asking
 * {@link CurrentEmployeeResolver} which store the admin administers, and everything downstream uses that
 * answer — the listing's scope and the new employee's {@code store_id} alike
 * ({@code docs/08-identidad-y-seguridad.md} §3 and §4, RF-11). There is no method that takes a store
 * identifier, which is what makes the legacy's habit of trusting a {@code tiendaId} from the request
 * impossible to reintroduce here by accident.
 *
 * <p>Editing and deleting an employee are <strong>out of scope for v1.0</strong> and their absence is a
 * decision, not a gap (RF-11): with identity in another service, deleting an employee means disabling
 * their account in ukun-auth and removing their profile here — the inverse of this saga, with its own
 * compensation, which does not fit in a {@code DELETE} on a table.
 */
@Service
public class AdminEmployeeService {

    /**
     * The listing has no sort parameter in the contract, so the order is fixed here — and it ends in
     * {@code id} because defect #23 is a page-turning bug, not a catalogue one: two employees called
     * "García" with no tie-breaker can appear on two pages or on none.
     */
    private static final Sort BY_NAME = Sort.by("name", "lastName", "id");

    /** An employee is always an {@code EMPLOYEE}; {@code ADMIN} is the sub-flag of RF-12 on top. */
    private static final Set<Role> EMPLOYEE_ONLY = Set.of(Role.EMPLOYEE);

    private static final Set<Role> EMPLOYEE_AND_ADMIN = Set.of(Role.EMPLOYEE, Role.ADMIN);

    private final CurrentEmployeeResolver admins;
    private final EmployeeRepository employees;
    private final AccountRegistrationService registrations;
    private final EmployeeRegistrar registrar;
    private final EmployeeMapper mapper;

    AdminEmployeeService(
            CurrentEmployeeResolver admins,
            EmployeeRepository employees,
            AccountRegistrationService registrations,
            EmployeeRegistrar registrar,
            EmployeeMapper mapper) {

        this.admins = admins;
        this.employees = employees;
        this.registrations = registrations;
        this.registrar = registrar;
        this.mapper = mapper;
    }

    /** The staff of the admin's own store, filtered by the two optional criteria of {@link EmployeeFilters}. */
    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> list(EmployeeFilters filters, PaginationParams pagination) {
        Long storeId = admins.requireStoreOfCurrentAdmin();

        Page<Employee> page = employees.findAll(specificationFor(filters, storeId), pagination.toPageable(BY_NAME));

        return PageResponse.of(page, mapper::toResponse);
    }

    /**
     * The alta of RF-11, which is the registration saga of {@code docs/08} §3 with two changes: the
     * roles of an employee, and a store nobody chose.
     *
     * <p><strong>Deliberately not {@code @Transactional}</strong>, which is why this class carries no
     * class-level annotation either: the saga makes two calls to ukun-auth, and a transaction spanning
     * them would hold a database connection open across a network round trip. The only transaction is
     * {@link EmployeeRegistrar}'s.
     *
     * <p>Email uniqueness is not checked here and must not be: it belongs to the database that owns the
     * constraint. Asking first would reintroduce the legacy {@code EmpleadoValidator}'s race across a
     * network as well, and ADR-0006 — customer and employee are mutually exclusive — comes for free
     * from the same place, because an email already used by a customer already has an account.
     *
     * @throws EmailAlreadyExistsException propagated from ukun-auth as {@code 409}, which the frontend
     *     paints under the email field
     */
    public EmployeeCreation create(CreateEmployeeRequest request) {
        Long storeId = admins.requireStoreOfCurrentAdmin();

        Registration registration = registrations.register(
                new CreateAccountCommand(request.email(), request.password(), rolesFor(request), false),
                accountId -> registrar.register(accountId, request, storeId));

        return new EmployeeCreation(
                mapper.toResponse(registration.localId(), request, storeId), registration.outcome());
    }

    /**
     * The flag of RF-12 and the roles of the account are two spellings of one decision, in two
     * databases: {@code employee.is_admin} here, {@code account_role} there. They are written by the
     * same request on purpose — the day they can be set apart is the day one of them is a lie.
     */
    private static Set<Role> rolesFor(CreateEmployeeRequest request) {
        return Boolean.TRUE.equals(request.isAdmin()) ? EMPLOYEE_AND_ADMIN : EMPLOYEE_ONLY;
    }

    /** The store scope is not optional and is applied first; the other two only if they were sent. */
    private static Specification<Employee> specificationFor(EmployeeFilters filters, Long storeId) {
        Specification<Employee> specification = EmployeeSpecifications.worksIn(storeId);

        if (filters.q() != null) {
            specification = specification.and(EmployeeSpecifications.matches(filters.q()));
        }
        if (filters.isAdmin() != null) {
            specification = specification.and(EmployeeSpecifications.isAdmin(filters.isAdmin()));
        }
        return specification;
    }
}
