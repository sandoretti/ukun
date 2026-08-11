package dev.sandoretti.ukun.api.customers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

import dev.sandoretti.ukun.api.AbstractIntegrationTest;
import dev.sandoretti.ukun.api.identity.AccountRef;
import dev.sandoretti.ukun.api.identity.AccountRejectedException;
import dev.sandoretti.ukun.api.identity.AccountsPort;
import dev.sandoretti.ukun.api.identity.CreateAccountCommand;
import dev.sandoretti.ukun.api.identity.Role;
import dev.sandoretti.ukun.api.identity.EmailAlreadyExistsException;
import dev.sandoretti.ukun.api.shared.error.ValidationError;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The orchestrating half of the signup saga: {@code POST /api/signup}, against a real database.
 *
 * <p>{@link AccountsPort} is replaced with a double, which is exactly the case
 * {@code docs/13-testing.md} §"Mocks solo en fronteras" allows — it is one of the two external
 * boundaries of this service, and its real implementation talks HTTP to another process. That is also
 * what makes the interesting tests possible: the failures this saga exists to survive (the local
 * transaction dying after the reservation, the activation never confirming) cannot be provoked against
 * a healthy ukun-auth, and a saga only tested on its happy path is a saga whose compensation has never
 * run.
 *
 * <p>The server half — that a replayed key returns the same account, that a reserved email is not
 * handed to another attempt — is asserted in ukun-auth's {@code InternalAccountsIT}, against the real
 * database that enforces it. Neither suite asserts the other's half twice.
 */
@AutoConfigureRestTestClient
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.liquibase.contexts=prod")
class SignupIT extends AbstractIntegrationTest {

    private static final String SIGNUP = "/api/signup";

    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {};

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        useDatabase(registry, SIGNUP_DATABASE);
    }

    @Autowired
    private RestTestClient http;

    @MockitoBean
    private AccountsPort accounts;

    private UUID reservedAccount;

    @BeforeEach
    void reservationSucceedsByDefault() {
        reservedAccount = UUID.randomUUID();
        given(accounts.reserve(any(), any())).willReturn(new AccountRef(reservedAccount, true));
    }

    @Test
    @DisplayName("a signup writes the profile, its address and its card, and answers 201")
    void createsTheCustomerAndActivatesTheAccount() {
        signup(aSignup()).expectStatus().isCreated();

        Map<String, Object> customer = customerOf(reservedAccount);

        assertThat(customer).containsEntry("name", "Ada").containsEntry("last_name", "Lovelace");
        // The three rows of the single local transaction: the profile and the two it owns.
        assertThat(customer.get("address_id")).isNotNull();
        assertThat(customer.get("card_id")).isNotNull();
        // The order of the saga, asserted where it is observable: the account was activated, and the
        // activation came after the profile existed.
        then(accounts).should().activate(reservedAccount);
    }

    /**
     * The reservation carries the roles of a customer and the {@code enabled=false} of step 2 — a
     * reservation that arrived enabled would be a live login for a profile that does not exist yet.
     */
    @Test
    @DisplayName("the account is reserved as a disabled CUSTOMER before anything is written")
    void reservesADisabledCustomerAccount() {
        signup(aSignup()).expectStatus().isCreated();

        var command = ArgumentCaptor.forClass(CreateAccountCommand.class);
        then(accounts).should().reserve(any(), command.capture());

        assertThat(command.getValue().enabled()).isFalse();
        assertThat(command.getValue().roles()).containsExactly(Role.CUSTOMER);
    }

    /**
     * Uniqueness belongs to the other database, so it arrives as an exception and leaves as the code
     * the frontend paints under the email field — not as {@code DUPLICATE_VALUE}, which is for
     * conflicts this service decides locally ({@code docs/12-error-contract.md} §2).
     */
    @Test
    @DisplayName("a taken email is propagated as 409 EMAIL_ALREADY_EXISTS and writes nothing")
    void propagatesATakenEmail() {
        willThrow(new EmailAlreadyExistsException()).given(accounts).reserve(any(), any());

        Map<String, Object> problem = signup(aSignup())
                .expectStatus()
                .isEqualTo(HttpStatus.CONFLICT)
                .expectBody(JSON)
                .returnResult()
                .getResponseBody();

        assertThat(problem).containsEntry("code", "EMAIL_ALREADY_EXISTS");
        assertThat(profilesOf(reservedAccount)).isZero();
    }

    /**
     * The password policy belongs to ukun-auth, so its verdict arrives as an exception from the port
     * and has to leave as a {@code 400} naming the field — the same shape as a form rejected here, so
     * the frontend cannot tell which service decided and does not have to.
     */
    @Test
    @DisplayName("a password ukun-auth rejects is a 400 on the password field")
    void propagatesARejectedPassword() {
        willThrow(new AccountRejectedException(
                        List.of(new ValidationError("password", "must be at least 12 characters long"))))
                .given(accounts)
                .reserve(any(), any());

        Map<String, Object> problem = signup(aSignup())
                .expectStatus()
                .isBadRequest()
                .expectBody(JSON)
                .returnResult()
                .getResponseBody();

        assertThat(problem).containsEntry("code", "VALIDATION_FAILED");
        assertThat(fieldsOf(problem)).containsExactly("password");
        assertThat(profilesOf(reservedAccount)).isZero();
    }

    /**
     * <strong>The test the issue is named after.</strong> The local transaction fails after the
     * reservation, which is the moment the saga is most exposed: an account exists in one database and
     * nothing in the other.
     *
     * <p>The failure is provoked through a constraint the database really enforces —
     * {@code ux_customer_account_id}, by planting a profile that already claims the account ukun-auth
     * is about to hand back. The transaction then dies inside the flush, the way a real one would,
     * rather than because a repository was stubbed to throw. What is under test is the saga's answer to
     * that: release the reservation, leave no half-written profile.
     *
     * <p>Note what is <em>not</em> asserted: that the release succeeded. It is best-effort, and the
     * guarantee that no orphan account survives is ukun-auth's reconciliation job, tested there.
     */
    @Test
    @DisplayName("a local failure after the reservation releases the account and leaves no orphan")
    void compensatesWhenTheLocalTransactionFails() {
        plantAProfileClaiming(reservedAccount);

        // A street only this test uses: the class shares one database, so counting rows by a value the
        // happy path also writes would be counting somebody else's success.
        String street = "Calle Rollback " + UUID.randomUUID();
        Map<String, Object> request = aSignup();
        @SuppressWarnings("unchecked")
        Map<String, Object> address = (Map<String, Object>) request.get("address");
        address.put("street", street);

        signup(request).expectStatus().is5xxServerError();

        // Still one: the planted row. And the address the request carried is nowhere — the insert of
        // the profile, its address and its card rolled back as the single transaction it is, rather
        // than leaving the two rows that had already succeeded when the third failed.
        assertThat(profilesOf(reservedAccount)).isEqualTo(1);
        assertThat(addressesAt(street)).isZero();
        then(accounts).should().release(reservedAccount);
        then(accounts).should(never()).activate(any());
    }

    /**
     * The activation never lands. The answer is {@code 202} and not an error, because the customer
     * <em>does</em> exist: telling them something went wrong would invite a second signup with a
     * different email, which is the one outcome nobody wants.
     */
    @Test
    @DisplayName("an activation that never confirms answers 202 and keeps the customer")
    void answers202WhenTheActivationCannotBeConfirmed() {
        doThrow(new IllegalStateException("ukun-auth is down")).when(accounts).activate(any());

        signup(aSignup()).expectStatus().isAccepted();

        // The profile is written and safe. Repeating the same signup activates the account.
        assertThat(customerOf(reservedAccount)).isNotNull();
    }

    /**
     * Step 0 of {@code docs/08} §3. A store that does not exist is a bad field in a form, so it is a
     * {@code 400} naming the field — not the {@code 409 REFERENTIAL_INTEGRITY} the foreign key would
     * produce if the check were left to the insert.
     */
    @Test
    @DisplayName("an unknown favourite store is a 400 naming the field, and nothing is reserved")
    void rejectsAnUnknownFavouriteStore() {
        Map<String, Object> request = aSignup();
        request.put("favoriteStoreId", 999_999L);

        Map<String, Object> problem = signup(request)
                .expectStatus()
                .isBadRequest()
                .expectBody(JSON)
                .returnResult()
                .getResponseBody();

        assertThat(problem).containsEntry("code", "VALIDATION_FAILED");
        assertThat(fieldsOf(problem)).containsExactly("favoriteStoreId");
        // Validated before anything crosses the network: a bad form costs no account.
        then(accounts).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("an existing favourite store is stored on the profile")
    void keepsAValidFavouriteStore() {
        long storeId = aStore();

        Map<String, Object> request = aSignup();
        request.put("favoriteStoreId", storeId);

        signup(request).expectStatus().isCreated();

        assertThat(customerOf(reservedAccount)).containsEntry("favorite_store_id", storeId);
    }

    /** The password never leaves the request: it is not a column of this database at all. */
    @Test
    @DisplayName("a malformed email is rejected before the saga starts")
    void rejectsAMalformedEmail() {
        Map<String, Object> request = aSignup();
        request.put("email", "not-an-email");

        signup(request).expectStatus().isBadRequest();

        then(accounts).shouldHaveNoInteractions();
    }

    // ---------------------------------------------------------------------------------------------

    private RestTestClient.ResponseSpec signup(Map<String, Object> request) {
        return http.post()
                .uri(SIGNUP)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange();
    }

    /** Mutable on purpose: each test bends one field of an otherwise valid form. */
    private static Map<String, Object> aSignup() {
        Map<String, Object> address = new HashMap<>(Map.of(
                "street", "Calle Mayor 1",
                "city", "Alcalá de Henares",
                "province", "Madrid",
                "postalCode", "28801"));

        Map<String, Object> card = new HashMap<>(Map.of(
                "last4", "4242",
                "brand", "VISA",
                "expMonth", 12,
                "expYear", 2030));

        Map<String, Object> request = new HashMap<>();
        request.put("email", "ada-" + UUID.randomUUID() + "@ukun.dev");
        request.put("password", "correct-horse-battery");
        request.put("name", "Ada");
        request.put("lastName", "Lovelace");
        request.put("address", address);
        request.put("card", card);

        return request;
    }

    private Map<String, Object> customerOf(UUID accountId) {
        return jdbc.queryForMap("SELECT * FROM customer WHERE account_id = ?", accountId);
    }

    /**
     * Counted by account rather than over the whole table: every test mints its own account id, so the
     * assertions stay true whatever else the class has written and in whatever order it ran.
     */
    private int profilesOf(UUID accountId) {
        return jdbc.queryForObject("SELECT count(*) FROM customer WHERE account_id = ?", Integer.class, accountId);
    }

    private int addressesAt(String street) {
        return jdbc.queryForObject("SELECT count(*) FROM address WHERE street = ?", Integer.class, street);
    }

    /**
     * A store to be somebody's favourite. Written through JDBC rather than with {@code StoreMother}
     * plus a repository: {@code Store.address} is a {@code @ManyToOne} with no cascade, so saving the
     * mother's object graph takes two persists in a transaction — machinery this class has no other
     * use for, and which {@code StoresIntegrationTest} already owns for the suite that does.
     */
    private long aStore() {
        Long addressId = jdbc.queryForObject(
                """
                INSERT INTO address (street, city, province, postal_code)
                VALUES ('Calle Libreros 34', 'Alcalá de Henares', 'Madrid', '28801') RETURNING id
                """,
                Long.class);

        return jdbc.queryForObject(
                """
                INSERT INTO store (name, phone, is_online, address_id)
                VALUES ('Ukun Alcalá', '+34918881234', false, ?) RETURNING id
                """,
                Long.class,
                addressId);
    }

    /**
     * A profile already holding {@code accountId}, written straight through JDBC so it exists before
     * the request arrives. {@code ux_customer_account_id} is what makes the saga's own insert fail.
     */
    private void plantAProfileClaiming(UUID accountId) {
        Long addressId = jdbc.queryForObject(
                """
                INSERT INTO address (street, city, province, postal_code)
                VALUES ('Calle Planted 1', 'Alcalá de Henares', 'Madrid', '28801') RETURNING id
                """,
                Long.class);
        Long cardId = jdbc.queryForObject(
                """
                INSERT INTO card (last4, brand, exp_month, exp_year)
                VALUES ('0000', 'VISA', 1, 2030) RETURNING id
                """,
                Long.class);

        jdbc.update(
                """
                INSERT INTO customer (account_id, name, last_name, email, address_id, card_id)
                VALUES (?, 'Planted', 'Profile', ?, ?, ?)
                """,
                accountId,
                "planted-" + accountId + "@ukun.dev",
                addressId,
                cardId);
    }

    @SuppressWarnings("unchecked")
    private static List<String> fieldsOf(Map<String, Object> problem) {
        return ((List<Map<String, Object>>) problem.get("errors"))
                .stream().map(error -> (String) error.get("field")).toList();
    }
}
