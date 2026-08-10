package dev.sandoretti.ukun.api.customers;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sandoretti.ukun.api.customers.application.CurrentUserResponse;
import dev.sandoretti.ukun.api.customers.domain.ProfileLocale;
import dev.sandoretti.ukun.api.identity.Role;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/**
 * {@code GET /api/me}: the session probe the SPA calls right after login ({@code docs/01-arquitectura.md}
 * §2). It answers customers and employees alike, which is what separates it from the profile.
 */
class CurrentUserIT extends ProfileIntegrationTest {

    private static final UUID ADA = UUID.fromString("8f2c1e40-0000-4000-8000-0000000000a1");
    private static final UUID EMPLOYEE = UUID.fromString("8f2c1e40-0000-4000-8000-0000000000e1");

    /**
     * The locale is set to English before asking, and that is the point of the test rather than a
     * detail: with a customer left on the default there would be no way to tell "read from their
     * profile" apart from "hardcoded", which is exactly what an employee gets.
     */
    @Test
    @DisplayName("a customer is identified by their account, with the language stored in their profile")
    void answersACustomerWithTheirOwnLocale() {
        seed(CustomerMother.ada(ADA));
        setLocale(ADA, ProfileLocale.EN);

        CurrentUserResponse ada = currentUser(customerToken(ADA, "ada@ukun.dev"));

        assertThat(ada.accountId()).isEqualTo(ADA);
        assertThat(ada.email()).isEqualTo("ada@ukun.dev");
        assertThat(ada.roles()).containsExactly(Role.CUSTOMER);
        assertThat(ada.locale()).isEqualTo(ProfileLocale.EN);
    }

    /**
     * An employee has no {@code customer} row and no {@code locale} column to read one from — the
     * baseline leaves it out on purpose (ADR-0019). The default is the answer, not an error: this
     * endpoint is how the SPA finds out it is talking to somebody who may see the back-office.
     */
    @Test
    @DisplayName("an employee is identified with the default language, not with a failure")
    void answersAnEmployeeWithTheDefaultLocale() {
        CurrentUserResponse employee = currentUser(employeeToken(EMPLOYEE, "employee@ukun.dev"));

        assertThat(employee.accountId()).isEqualTo(EMPLOYEE);
        assertThat(employee.roles()).containsExactly(Role.EMPLOYEE);
        assertThat(employee.locale()).isEqualTo(ProfileLocale.ES);
    }

    @Test
    @DisplayName("without a token there is nobody to identify: 401 UNAUTHENTICATED")
    void rejectsAnAnonymousCaller() {
        restTestClient
                .get()
                .uri(ME)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.traceId")
                .exists();
    }

    private CurrentUserResponse currentUser(String token) {
        return restTestClient
                .get()
                .uri(ME)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(CurrentUserResponse.class)
                .returnResult()
                .getResponseBody();
    }
}
