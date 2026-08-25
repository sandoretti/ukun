package dev.sandoretti.ukun.api.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code GET /api/me/employment} (RF-09): which store the employee behind the token works in.
 *
 * <p><strong>The store is never in the request</strong>, so there is nothing here to send wrong — and
 * that is precisely what makes these tests worth writing with real tokens rather than an injected
 * authentication: resolving {@code sub} against {@code employee.account_id} <em>is</em> the whole
 * endpoint, and mocking the authentication would skip it.
 *
 * <p>Two employees of two different stores answer the question that a single-store fixture could not:
 * Elena gets Barcelona, so an implementation that returned "a store" — the first row, the online one —
 * would be red here instead of plausibly green.
 */
class MyEmploymentIT extends StaffIntegrationTest {

    private static final String EMPLOYMENT = "/api/me/employment";

    /**
     * Diego administers nothing, which is the point: the four stock screens are for any employee, so a
     * {@code hasRole('ADMIN')} slipped into this endpoint would lock out most of the people it is for
     * (ADR-0020, and the permission matrix of {@code docs/02-requisitos.md} §3).
     */
    @Test
    @DisplayName("an employee who is not an admin gets their own store")
    void answersTheStoreOfAnOrdinaryEmployee() {
        Employment employment = employmentOf(employeeToken(DIEGO));

        assertThat(employment.storeId()).isEqualTo(alcalaId());
        assertThat(employment.storeName()).isEqualTo(ALCALA);
        assertThat(employment.employeeId()).isEqualTo(employeeIdOf(DIEGO));
    }

    @Test
    @DisplayName("an admin gets their own store and not somebody else's")
    void answersTheStoreOfEachAdmin() {
        assertThat(employmentOf(adminToken(CARMEN)).storeName()).isEqualTo(ALCALA);
        assertThat(employmentOf(adminToken(ELENA)).storeName()).isEqualTo(BARCELONA);
    }

    /**
     * The role is in the token, so {@code @PreAuthorize} lets this one through; what stops it is the
     * employee row that is not there. It is a {@code 403} and not a {@code 404}: the resource being
     * asked for is the caller's own employment.
     */
    @Test
    @DisplayName("an employee token with no employee row behind it is denied")
    void deniesAnEmployeeWhoIsNobodyHere() {
        get(EMPLOYMENT, employeeToken(GHOST)).expectStatus().isForbidden();
    }

    @Test
    @DisplayName("a customer is denied: they have no employment to report")
    void deniesACustomer() {
        get(EMPLOYMENT, customerToken(UUID.randomUUID())).expectStatus().isForbidden();
    }

    @Test
    @DisplayName("an anonymous request is unauthenticated, not forbidden")
    void deniesAnonymousRequests() {
        restTestClient.get().uri(EMPLOYMENT).exchange().expectStatus().isUnauthorized();
    }

    /**
     * The {@code 403} carries the error contract's body like every other denial: a client that reads
     * {@code code} to decide what to paint has to find it here too ({@code docs/12-error-contract.md}).
     */
    @Test
    @DisplayName("the denial is a ProblemDetail with the FORBIDDEN code")
    void deniesWithTheErrorContract() {
        get(EMPLOYMENT, customerToken(UUID.randomUUID()))
                .expectStatus()
                .isForbidden()
                .expectHeader()
                .contentTypeCompatibleWith("application/problem+json")
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("FORBIDDEN");
    }

    private Employment employmentOf(String token) {
        return get(EMPLOYMENT, token)
                .expectStatus()
                .isOk()
                .expectBody(Employment.class)
                .returnResult()
                .getResponseBody();
    }

    /** Read off the table the endpoint reports, so the assertion is not comparing the answer to itself. */
    private long employeeIdOf(UUID accountId) {
        return jdbc.queryForObject("SELECT id FROM employee WHERE account_id = ?", Long.class, accountId);
    }

    /**
     * Declared here rather than reusing {@code EmploymentResponse}, for the reason
     * {@code EmployeePage} gives: a field the production record stops publishing has to show up as a
     * missing value in a test, not as a type that changed underneath it.
     */
    private record Employment(Long employeeId, Long storeId, String storeName) {}
}
