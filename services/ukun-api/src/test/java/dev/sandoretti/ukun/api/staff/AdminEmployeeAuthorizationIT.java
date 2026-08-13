package dev.sandoretti.ukun.api.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Who gets in, on both operations of RF-11.
 *
 * <p>Two rules are being checked and they fail at different depths, which is why the same {@code 403}
 * is asserted from four directions. The role rule (ADR-0020, {@code @PreAuthorize}) stops a customer
 * and an ordinary employee before the method runs. The resource rule
 * ({@code docs/08-identidad-y-seguridad.md} §4) stops a token that carries {@code ADMIN} and belongs to
 * nobody in this database — an employee whose row was removed, or a role granted out of band. A service
 * that trusted the token's claims instead of resolving them would let that last one through and then
 * have to invent a store for them.
 *
 * <p>Every denial of the alta also asserts that the store's headcount did not change: a {@code 403}
 * that wrote a row first would still look like a {@code 403} to the caller.
 */
class AdminEmployeeAuthorizationIT extends StaffIntegrationTest {

    @Test
    @DisplayName("an employee who is not an admin cannot list the staff")
    void deniesListingToAnEmployee() {
        get(EMPLOYEES, employeeToken(DIEGO)).expectStatus().isForbidden();
    }

    @Test
    @DisplayName("a customer cannot list the staff")
    void deniesListingToACustomer() {
        get(EMPLOYEES, customerToken(UUID.randomUUID())).expectStatus().isForbidden();
    }

    /**
     * The role is in the token, so {@code @PreAuthorize} lets this one through; what stops it is the
     * store that cannot be resolved. Both denials answer the same body on purpose — telling the caller
     * which of the two it was would confirm to a stranger that the other check passed.
     */
    @Test
    @DisplayName("an admin token with no employee row behind it is denied")
    void deniesAnAdminWhoIsNobodyHere() {
        get(EMPLOYEES, adminToken(GHOST)).expectStatus().isForbidden();
    }

    @Test
    @DisplayName("an anonymous request is unauthenticated, not forbidden")
    void deniesAnonymousRequests() {
        restTestClient.get().uri(EMPLOYEES).exchange().expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("nobody but an admin of the store can create an employee, and no row is written")
    void deniesCreatingToEverybodyElse() {
        long alcala = alcalaId();
        int before = headcountOf(alcala);

        create(employeeToken(DIEGO)).expectStatus().isForbidden();
        create(customerToken(UUID.randomUUID())).expectStatus().isForbidden();
        create(adminToken(GHOST)).expectStatus().isForbidden();

        assertThat(headcountOf(alcala)).isEqualTo(before);
    }

    private RestTestClient.ResponseSpec create(String token) {
        return restTestClient
                .post()
                .uri(EMPLOYEES)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", "nuevo@ukun.com",
                        "password", "una-contrasena-larga",
                        "name", "Nuevo",
                        "lastName", "Empleado",
                        "jobTitle", "Dependiente",
                        "isAdmin", false))
                .exchange();
    }
}
