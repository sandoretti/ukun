package dev.sandoretti.ukun.api.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code GET /api/admin/employees} (RF-11): the staff of the admin's own store, and only that.
 *
 * <p>The store is never in the request, so every test here is implicitly about the same thing — that
 * the scope comes from the token — and two of them are about it explicitly: Elena works in Barcelona
 * and cannot appear in a listing Carmen asked for, no matter what that request says, because there is
 * nothing it could say.
 *
 * <p><strong>Nothing here asserts a collation.</strong> The order the endpoint fixes is
 * {@code name, lastName, id}, but whether {@code Ángela} sorts before or after {@code Carmen} is the
 * database's locale talking, not this slice — so the ordering test asserts the property that
 * {@code id} was added for (defect #23: every row exactly once across the pages) rather than a
 * sequence of names that would break the day the image changes its locale.
 */
class AdminEmployeeListingIT extends StaffIntegrationTest {

    @Test
    @DisplayName("an admin sees the staff of their own store and nobody else's")
    void listsOnlyTheStaffOfTheAdminsStore() {
        EmployeePage page = listing(EMPLOYEES, CARMEN);

        assertThat(namesOf(page)).containsExactlyInAnyOrder("Ángela", "Carmen", "Diego", "Luis", "Luis");
        assertThat(page.items())
                .extracting(EmployeePage.Employee::storeId)
                .containsOnly(alcalaId());
        assertThat(page.totalElements()).isEqualTo(5);
    }

    /**
     * The other side of the same rule, from the other store: a listing is not "the employee table
     * paginated", and Elena proves it by seeing exactly one person — herself.
     */
    @Test
    @DisplayName("each admin's listing is scoped to their own store")
    void scopesEachAdminToTheirOwnStore() {
        EmployeePage page = listing(EMPLOYEES, ELENA);

        assertThat(namesOf(page)).containsExactly("Elena");
    }

    /** Defect #22: the seeded staff is full of accents and nobody types them into a search box. */
    @Test
    @DisplayName("the search folds accents on the name, the last name and the email")
    void findsAccentedPeopleWithoutTheAccents() {
        assertThat(namesOf(EMPLOYEES + "?q=angela")).containsExactly("Ángela");
        assertThat(namesOf(EMPLOYEES + "?q=munoz")).containsExactly("Ángela");
        assertThat(namesOf(EMPLOYEES + "?q=carmen@ukun.com")).containsExactly("Carmen");
    }

    @Test
    @DisplayName("a term that matches nobody comes back as an empty page, not as everybody")
    void answersAnEmptyPageWhenNothingMatches() {
        EmployeePage page = listing(EMPLOYEES + "?q=nadie", CARMEN);

        assertThat(page.items()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }

    @Test
    @DisplayName("the isAdmin filter tells the two kinds of employee apart (RF-12)")
    void filtersByTheAdministratorFlag() {
        assertThat(namesOf(EMPLOYEES + "?isAdmin=true")).containsExactly("Carmen");
        assertThat(namesOf(EMPLOYEES + "?isAdmin=false"))
                .containsExactlyInAnyOrder("Ángela", "Diego", "Luis", "Luis");
    }

    /**
     * Defect #23. The two Luises share a name and a last name, so without {@code id} as the last sort
     * criterion the database is free to order them differently between two requests — and one of them
     * comes back on two pages while the other comes back on none.
     */
    @Test
    @DisplayName("paging through a real tie returns every employee exactly once")
    void pagesStablyThroughEmployeesWithTheSameName() {
        EmployeePage first = listing(EMPLOYEES + "?page=0&size=2", CARMEN);
        EmployeePage second = listing(EMPLOYEES + "?page=1&size=2", CARMEN);
        EmployeePage third = listing(EMPLOYEES + "?page=2&size=2", CARMEN);

        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.items()).hasSize(2);
        assertThat(second.items()).hasSize(2);
        assertThat(third.items()).hasSize(1);

        assertThat(idsOf(first, second, third)).doesNotHaveDuplicates().hasSize(5);
    }

    private List<String> namesOf(String uri) {
        return namesOf(listing(uri, CARMEN));
    }

    private static List<String> namesOf(EmployeePage page) {
        return page.items().stream().map(EmployeePage.Employee::name).toList();
    }

    private static List<Long> idsOf(EmployeePage... pages) {
        return List.of(pages).stream()
                .flatMap(page -> page.items().stream())
                .map(EmployeePage.Employee::id)
                .toList();
    }

    private EmployeePage listing(String uri, UUID admin) {
        return get(uri, adminToken(admin))
                .expectStatus()
                .isOk()
                .expectBody(EmployeePage.class)
                .returnResult()
                .getResponseBody();
    }
}
