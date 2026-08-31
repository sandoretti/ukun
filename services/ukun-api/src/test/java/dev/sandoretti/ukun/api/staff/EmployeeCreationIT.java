package dev.sandoretti.ukun.api.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import dev.sandoretti.ukun.api.identity.AccountRef;
import dev.sandoretti.ukun.api.audit.AuditRows;
import dev.sandoretti.ukun.api.identity.AccountsPort;
import dev.sandoretti.ukun.api.identity.CreateAccountCommand;
import dev.sandoretti.ukun.api.identity.EmailAlreadyExistsException;
import dev.sandoretti.ukun.api.identity.Role;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * {@code POST /api/admin/employees} (RF-11): the alta, which is the registration saga of
 * {@code docs/08-identidad-y-seguridad.md} §3 with the roles of an employee and a store nobody chose.
 *
 * <p>{@link AccountsPort} is replaced with a double, the boundary {@code docs/13-testing.md} allows and
 * the same one {@code SignupIT} uses: it is one of the two external boundaries of this service, and the
 * failures the saga exists to survive — the local write dying after the reservation, the activation
 * never confirming — cannot be provoked against a healthy ukun-auth.
 *
 * <p>What is <strong>not</strong> re-asserted here is the saga's own machinery: that a replayed
 * idempotency key returns the same account is ukun-auth's {@code InternalAccountsIT}, and that a failed
 * local write releases the reservation is {@code AccountRegistrationServiceTest}, one level down. This
 * suite is about the two things only the back-office decides: which roles the account is born with, and
 * which store the employee lands in.
 */
class EmployeeCreationIT extends StaffIntegrationTest {

    @MockitoBean
    private AccountsPort accounts;

    private UUID reservedAccount;

    @BeforeEach
    void reservationSucceedsByDefault() {
        reservedAccount = UUID.randomUUID();
        given(accounts.reserve(any(), any())).willReturn(new AccountRef(reservedAccount, true));
    }

    @Test
    @DisplayName("an alta writes the employee, activates the account and answers 201 with the employee")
    void createsTheEmployeeAndActivatesTheAccount() {
        create(anEmployee())
                .expectStatus()
                .isCreated()
                .expectHeader()
                .value(HttpHeaders.LOCATION, location -> assertThat(location).startsWith(EMPLOYEES + "/"))
                .expectBody()
                .jsonPath("$.name")
                .isEqualTo("Nuria")
                .jsonPath("$.jobTitle")
                .isEqualTo("Dependienta")
                .jsonPath("$.isAdmin")
                .isEqualTo(false)
                .jsonPath("$.storeId")
                .isEqualTo(alcalaId());

        then(accounts).should().activate(reservedAccount);
    }

    /**
     * The reservation carries the roles of an employee and the {@code enabled=false} of step 2 — a
     * reservation that arrived enabled would be a live login for a profile that does not exist yet.
     */
    @Test
    @DisplayName("the account is reserved as a disabled EMPLOYEE before anything is written")
    void reservesADisabledEmployeeAccount() {
        create(anEmployee()).expectStatus().isCreated();

        assertThat(reservation().enabled()).isFalse();
        assertThat(reservation().roles()).containsExactly(Role.EMPLOYEE);
    }

    /**
     * The flag of RF-12 and the account's roles are two spellings of one decision, in two databases. An
     * admin created here must be an {@code ADMIN} there, or the person can administer a store the token
     * will not let them into.
     */
    @Test
    @DisplayName("creating an admin sets the flag here and the ADMIN role in ukun-auth")
    void createsAnAdminOnBothSides() {
        Map<String, Object> request = anEmployee();
        request.put("isAdmin", true);

        create(request).expectStatus().isCreated();

        assertThat(reservation().roles()).containsExactlyInAnyOrder(Role.EMPLOYEE, Role.ADMIN);
        assertThat(jdbc.queryForObject(
                        "SELECT is_admin FROM employee WHERE account_id = ?", Boolean.class, reservedAccount))
                .isTrue();
    }

    /**
     * The rule of {@code docs/08} §3 and §4, from the only angle a client can attack it: the body has no
     * {@code storeId}, so sending one is not a way to put somebody in another store — it is a field
     * nobody reads. Barcelona's headcount is what proves it.
     */
    @Test
    @DisplayName("a storeId in the body is ignored: the employee lands in the admin's store")
    void ignoresAStoreIdSentInTheBody() {
        long barcelona = storeIdOf(BARCELONA);
        Map<String, Object> request = anEmployee();
        request.put("storeId", barcelona);

        create(request).expectStatus().isCreated();

        assertThat(jdbc.queryForObject(
                        "SELECT store_id FROM employee WHERE account_id = ?", Long.class, reservedAccount))
                .isEqualTo(alcalaId());
        assertThat(headcountOf(barcelona)).isEqualTo(1);
    }

    /**
     * Uniqueness belongs to the other database, so it arrives as an exception and leaves as the code the
     * frontend paints under the email field — not as {@code DUPLICATE_VALUE}, which is for conflicts
     * this service decides locally ({@code docs/12-error-contract.md} §2).
     */
    @Test
    @DisplayName("a taken email is propagated as 409 EMAIL_ALREADY_EXISTS and writes nothing")
    void propagatesATakenEmail() {
        willThrow(new EmailAlreadyExistsException()).given(accounts).reserve(any(), any());
        int before = headcountOf(alcalaId());

        create(anEmployee())
                .expectStatus()
                .isEqualTo(409)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("EMAIL_ALREADY_EXISTS");

        assertThat(headcountOf(alcalaId())).isEqualTo(before);
        then(accounts).should(never()).activate(any());
    }

    /**
     * The {@code 202} of the contract: the employee exists and their account does not work yet. It is
     * not an error — the row is written and repeating the same alta activates the account — so the
     * assertion is that the person is in the table, not that the request failed.
     */
    @Test
    @DisplayName("an activation that never lands answers 202 with the employee already created")
    void answers202WhenTheActivationCannotBeConfirmed() {
        willThrow(new IllegalStateException("ukun-auth is down"))
                .given(accounts)
                .activate(reservedAccount);

        create(anEmployee()).expectStatus().isAccepted().expectBody().isEmpty();

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM employee WHERE account_id = ?", Integer.class, reservedAccount))
                .isEqualTo(1);
    }

    private CreateAccountCommand reservation() {
        ArgumentCaptor<CreateAccountCommand> command = ArgumentCaptor.forClass(CreateAccountCommand.class);
        then(accounts).should().reserve(any(), command.capture());

        return command.getValue();
    }

    /** Mutable on purpose: half the tests here are one field away from the plain case. */
    /**
     * The row the alta leaves in {@code audit_log} (ADR-0014). It lives in this class rather than in an
     * audit suite of its own because the assertion needs the {@code AccountsPort} double that makes an
     * alta possible at all, and a second class would be that fixture copied.
     *
     * <p><strong>Written inside the local transaction</strong>, which is what {@code EmployeeRegistrar}
     * is a bean for: an alta whose employee row rolls back leaves no audit of having created one.
     */
    @Test
    @DisplayName("an alta records EMPLOYEE_CREATED against the new employee, attributed to the admin")
    void auditsTheAlta() {
        AuditRows.clear(jdbc);

        create(anEmployee()).expectStatus().isCreated();

        AuditRows.Row row = AuditRows.only(jdbc);

        assertThat(row.action()).isEqualTo("EMPLOYEE_CREATED");
        assertThat(row.resourceType()).isEqualTo("employee");
        assertThat(row.actorAccountId()).isEqualTo(CARMEN);
        assertThat(row.actorRole()).isEqualTo("ADMIN");
        // The new employee's own account, and the store it landed in — never taken from the request.
        assertThat(AuditRows.detailsOf(row))
                .containsEntry("accountId", reservedAccount.toString())
                .containsEntry("storeId", (int) alcalaId())
                .containsEntry("isAdmin", false);
    }

    /**
     * The password travels in the request body of this very endpoint, which makes it the sharpest place
     * to assert the rule of {@code docs/11} §4: {@code details} is an open JSONB column and nothing
     * secret may reach it. The email is checked too — it is PII, and only the three events of §3 where
     * it is the subject may carry one.
     */
    @Test
    @DisplayName("the audited alta carries neither the password nor the email")
    void auditsWithoutTheCredentials() {
        AuditRows.clear(jdbc);

        create(anEmployee()).expectStatus().isCreated();

        assertThat(AuditRows.only(jdbc).details())
                .doesNotContain("una-contrasena-larga")
                .doesNotContain("password")
                .doesNotContain("nuria@ukun.com");
    }

    private static Map<String, Object> anEmployee() {
        Map<String, Object> request = new HashMap<>();
        request.put("email", "nuria@ukun.com");
        request.put("password", "una-contrasena-larga");
        request.put("name", "Nuria");
        request.put("lastName", "Vidal");
        request.put("jobTitle", "Dependienta");
        request.put("isAdmin", false);

        return request;
    }

    private RestTestClient.ResponseSpec create(Map<String, Object> request) {
        return restTestClient
                .post()
                .uri(EMPLOYEES)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken(CARMEN)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange();
    }
}
