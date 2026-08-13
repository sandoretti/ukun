package dev.sandoretti.ukun.api.staff.infra;

import dev.sandoretti.ukun.api.shared.persistence.TextSearch;
import dev.sandoretti.ukun.api.staff.domain.Employee;
import org.springframework.data.jpa.domain.Specification;

/**
 * The composable predicates of the employee listing (RF-11). The store one is not optional and never
 * becomes one — see {@link #worksIn}.
 */
public final class EmployeeSpecifications {

    private static final String NAME = "name";
    private static final String LAST_NAME = "lastName";
    private static final String EMAIL = "email";
    private static final String IS_ADMIN = "admin";
    private static final String STORE_ID = "storeId";

    private EmployeeSpecifications() {}

    /**
     * The scope of every listing of this module, and the only one there is: an admin sees their own
     * store's staff and nobody else's (RF-11).
     *
     * <p>It is applied by the use case from the store it resolved through the admin's {@code account_id},
     * never from anything the request carried — {@code docs/08-identidad-y-seguridad.md} §4. There is
     * no filter object component for the store precisely so that no request can ask for another one.
     */
    public static Specification<Employee> worksIn(Long storeId) {
        return (root, query, builder) -> builder.equal(root.get(STORE_ID), storeId);
    }

    /**
     * The free-text filter of the screen: name, last name or email, folded so that {@code "munoz"}
     * finds {@code "Muñoz"} (defect #22).
     *
     * <p>Three {@code OR}ed columns rather than a concatenation of them, because the folding is per
     * expression and a concatenated one would also match a term that straddles two fields — which is
     * not what the person typing a surname meant.
     */
    public static Specification<Employee> matches(String term) {
        return (root, query, builder) -> builder.or(
                TextSearch.containsIgnoringAccents(builder, root.get(NAME), term),
                TextSearch.containsIgnoringAccents(builder, root.get(LAST_NAME), term),
                TextSearch.containsIgnoringAccents(builder, root.get(EMAIL), term));
    }

    /** The administrator flag of RF-12, filtered in either direction. */
    public static Specification<Employee> isAdmin(boolean admin) {
        return (root, query, builder) -> builder.equal(root.get(IS_ADMIN), admin);
    }
}
