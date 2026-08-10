package dev.sandoretti.ukun.api.customers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import dev.sandoretti.ukun.api.customers.application.CustomerProfileResponse;
import dev.sandoretti.ukun.api.customers.application.ProfileAddress;
import dev.sandoretti.ukun.api.customers.application.ProfileCard;
import dev.sandoretti.ukun.api.customers.application.UpdateProfileRequest;
import dev.sandoretti.ukun.api.customers.domain.CardBrand;
import dev.sandoretti.ukun.api.customers.domain.ProfileLocale;
import java.util.UUID;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * {@code GET} and {@code PUT /api/me/profile} (RF-05), against a real database and real tokens.
 *
 * <p>The assertion this suite exists for is {@link #answersEachHolderTheirOwnProfile()}: the route has
 * no identifier in it, so the only thing deciding whose profile comes back is the {@code sub} claim
 * resolved against {@code customer.account_id} ({@code docs/08-identidad-y-seguridad.md} §4). Everything
 * else here is the contract's shape and RF-05's rules.
 */
class MyProfileIT extends ProfileIntegrationTest {

    private static final UUID ADA = UUID.fromString("8f2c1e40-0000-4000-8000-0000000000a1");
    private static final UUID GRACE = UUID.fromString("8f2c1e40-0000-4000-8000-0000000000a2");
    private static final UUID EMPLOYEE = UUID.fromString("8f2c1e40-0000-4000-8000-0000000000e1");
    private static final UUID STRANGER = UUID.fromString("8f2c1e40-0000-4000-8000-0000000000ff");

    @Test
    @DisplayName("the profile comes back with the card masked and nothing that is not in the contract")
    void readsTheProfileOfTheTokenHolder() {
        seed(CustomerMother.ada(ADA));

        restTestClient
                .get()
                .uri(MY_PROFILE)
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(ADA, "ada@ukun.dev")))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.name")
                .isEqualTo("Ada")
                .jsonPath("$.lastName")
                .isEqualTo("Lovelace")
                .jsonPath("$.email")
                .isEqualTo("ada@ukun.dev")
                .jsonPath("$.locale")
                .isEqualTo("es")
                .jsonPath("$.address.city")
                .isEqualTo("Alcalá de Henares")
                .jsonPath("$.card.last4")
                .isEqualTo("4242")
                .jsonPath("$.card.brand")
                .isEqualTo("VISA")
                .jsonPath("$.favoriteStoreId")
                .doesNotExist()
                // Not "absent because nobody filled them in": these fields do not exist in this system
                // at all, which is the fix for defect #15 (docs/08 §5, PCI-DSS).
                .jsonPath("$.card.number")
                .doesNotExist()
                .jsonPath("$.card.cvv")
                .doesNotExist()
                .jsonPath("$.password")
                .doesNotExist()
                .jsonPath("$.id")
                .doesNotExist()
                .jsonPath("$.accountId")
                .doesNotExist();
    }

    /**
     * Two customers, one route, no identifier anywhere in it. If ownership were ever resolved from
     * something the caller sends, this is the test that would go red.
     */
    @Test
    @DisplayName("each token holder gets their own profile from the same URL")
    void answersEachHolderTheirOwnProfile() {
        seed(CustomerMother.ada(ADA));
        seed(CustomerMother.grace(GRACE));

        CustomerProfileResponse ada = profileOf(customerToken(ADA, "ada@ukun.dev"));
        CustomerProfileResponse grace = profileOf(customerToken(GRACE, "grace@ukun.dev"));

        assertSoftly(softly -> {
            softly.assertThat(ada.name()).isEqualTo("Ada");
            softly.assertThat(ada.address().city()).isEqualTo("Alcalá de Henares");
            softly.assertThat(grace.name()).isEqualTo("Grace");
            softly.assertThat(grace.address().city()).isEqualTo("Barcelona");
        });
    }

    /**
     * An employee holds a valid token for this service and still has no profile here — the two roles are
     * mutually exclusive (ADR-0006) and this resource belongs to customers.
     */
    @Test
    @DisplayName("an employee is refused the customer profile with 403 FORBIDDEN")
    void refusesAnEmployee() {
        restTestClient
                .get()
                .uri(MY_PROFILE)
                .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken(EMPLOYEE, "employee@ukun.dev")))
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("FORBIDDEN");
    }

    @Test
    @DisplayName("without a token the profile is not even a 404: 401 UNAUTHENTICATED")
    void rejectsAnAnonymousCaller() {
        restTestClient
                .get()
                .uri(MY_PROFILE)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("UNAUTHENTICATED");
    }

    @Test
    @DisplayName("an edit rewrites name, address, card, favourite store and language")
    void updatesTheWholeProfile() {
        seed(CustomerMother.ada(ADA));
        String token = customerToken(ADA, "ada@ukun.dev");
        long barcelona = storeIdOf("Ukun Barcelona");

        CustomerProfileResponse updated = put(
                token,
                new UpdateProfileRequest(
                        "Augusta",
                        "King",
                        ProfileLocale.EN,
                        new ProfileAddress("Carrer de Mallorca 401", "Barcelona", "Barcelona", "08013"),
                        new ProfileCard("1234", CardBrand.AMEX, (short) 3, (short) 2031),
                        barcelona));

        assertSoftly(softly -> {
            softly.assertThat(updated.name()).isEqualTo("Augusta");
            softly.assertThat(updated.lastName()).isEqualTo("King");
            softly.assertThat(updated.locale()).isEqualTo(ProfileLocale.EN);
            softly.assertThat(updated.address().street()).isEqualTo("Carrer de Mallorca 401");
            softly.assertThat(updated.card().brand()).isEqualTo(CardBrand.AMEX);
            softly.assertThat(updated.favoriteStoreId()).isEqualTo(barcelona);
            // The email is the claim's and is not editable here: it is a credential and lives in
            // ukun-auth (ADR-0004), which is what closes the legacy "email already exists" bug (RF-06).
            softly.assertThat(updated.email()).isEqualTo("ada@ukun.dev");
        });

        assertThat(profileOf(token)).isEqualTo(updated);
    }

    /**
     * The address and the card are <em>edited</em>, not replaced by new rows. A profile that swapped
     * them would leave the old ones behind — pinned by {@code ON DELETE RESTRICT} and belonging to
     * nobody — and is the shape of mistake ADR-0011 exists to keep away from orders.
     */
    @Test
    @DisplayName("an edit keeps the same address and card rows")
    void editsTheAddressAndCardInPlace() {
        seed(CustomerMother.ada(ADA));
        String token = customerToken(ADA, "ada@ukun.dev");
        long addressId = jdbc.queryForObject("SELECT address_id FROM customer WHERE account_id = ?", Long.class, ADA);

        put(token, withStreet(unchanged(profileOf(token)), "Calle Mayor 2"));

        assertSoftly(softly -> {
            // Two store addresses and the customer's, not four: nothing was orphaned.
            softly.assertThat(jdbc.queryForObject("SELECT count(*) FROM address", Long.class))
                    .isEqualTo(3L);
            softly.assertThat(
                            jdbc.queryForObject("SELECT address_id FROM customer WHERE account_id = ?", Long.class, ADA))
                    .isEqualTo(addressId);
        });
    }

    /**
     * RF-05: "if no field changes, the response is 200 and nothing is persisted". Asserted as zero
     * writes rather than as an unchanged {@code updated_at}, because the second would also pass if the
     * row were rewritten with identical values.
     */
    @Test
    @DisplayName("saving a form with no edits answers 200 and writes nothing")
    void persistsNothingWhenNothingChanged() {
        seed(CustomerMother.ada(ADA));
        String token = customerToken(ADA, "ada@ukun.dev");
        UpdateProfileRequest untouched = unchanged(profileOf(token));

        Statistics statistics = freshStatistics();
        CustomerProfileResponse answer = put(token, untouched);

        assertSoftly(softly -> {
            softly.assertThat(answer.name()).isEqualTo("Ada");
            softly.assertThat(statistics.getEntityUpdateCount()).isZero();
            softly.assertThat(statistics.getEntityInsertCount()).isZero();
        });
    }

    /**
     * A store that does not exist is a bad value in a form, so it is a {@code 400} naming the field —
     * the same answer the signup gives it — and not a foreign key violation surfacing as a {@code 409}
     * with nothing to point at ({@code docs/08} §3, step 0).
     */
    @Test
    @DisplayName("a favourite store that does not exist is a field error, not a constraint violation")
    void rejectsAnUnknownFavouriteStore() {
        seed(CustomerMother.ada(ADA));
        String token = customerToken(ADA, "ada@ukun.dev");

        request(token, withFavoriteStore(unchanged(profileOf(token)), 999_999L))
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.errors[0].field")
                .isEqualTo("favoriteStoreId");
    }

    /**
     * The {@code PUT} is a replacement, so an absent favourite store clears it. Without this the choice
     * would be one-way and a customer could never undo it.
     */
    @Test
    @DisplayName("a null favourite store clears the one that was set")
    void clearsTheFavouriteStore() {
        seed(CustomerMother.ada(ADA));
        String token = customerToken(ADA, "ada@ukun.dev");
        put(token, withFavoriteStore(unchanged(profileOf(token)), storeIdOf("Ukun Alcalá de Henares")));

        CustomerProfileResponse cleared = put(token, withFavoriteStore(unchanged(profileOf(token)), null));

        assertThat(cleared.favoriteStoreId()).isNull();
    }

    /**
     * The write declares a {@code 404} and this is it: a token whose account has no profile behind it.
     * The read declares none and answers {@code 500}, because there the same state is this service's own
     * invariant broken rather than a resource the caller asked for and did not find.
     */
    @Test
    @DisplayName("editing a profile that does not exist is 404 RESOURCE_NOT_FOUND")
    void reportsAMissingProfileOnWrite() {
        seed(CustomerMother.ada(ADA));
        UpdateProfileRequest anyValidBody = unchanged(profileOf(customerToken(ADA, "ada@ukun.dev")));

        request(customerToken(STRANGER, "stranger@ukun.dev"), anyValidBody)
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("RESOURCE_NOT_FOUND")
                .jsonPath("$.resource")
                .isEqualTo("Customer")
                .jsonPath("$.id")
                .isEqualTo(STRANGER.toString());
    }

    private CustomerProfileResponse profileOf(String token) {
        return restTestClient
                .get()
                .uri(MY_PROFILE)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(CustomerProfileResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private CustomerProfileResponse put(String token, UpdateProfileRequest request) {
        return request(token, request)
                .expectStatus()
                .isOk()
                .expectBody(CustomerProfileResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private RestTestClient.ResponseSpec request(String token, UpdateProfileRequest request) {
        return restTestClient
                .put()
                .uri(MY_PROFILE)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange();
    }

    /**
     * The request the SPA sends when the form is opened and saved without an edit: the profile as it is,
     * echoed back. Built from the response rather than from constants so it cannot drift away from what
     * {@link CustomerMother} seeded.
     */
    private static UpdateProfileRequest unchanged(CustomerProfileResponse profile) {
        return new UpdateProfileRequest(
                profile.name(),
                profile.lastName(),
                profile.locale(),
                profile.address(),
                profile.card(),
                profile.favoriteStoreId());
    }

    /** One changed field at a time, so each test names the difference it is about and nothing else. */
    private static UpdateProfileRequest withStreet(UpdateProfileRequest request, String street) {
        ProfileAddress address = request.address();

        return new UpdateProfileRequest(
                request.name(),
                request.lastName(),
                request.locale(),
                new ProfileAddress(street, address.city(), address.province(), address.postalCode()),
                request.card(),
                request.favoriteStoreId());
    }

    private static UpdateProfileRequest withFavoriteStore(UpdateProfileRequest request, Long storeId) {
        return new UpdateProfileRequest(
                request.name(),
                request.lastName(),
                request.locale(),
                request.address(),
                request.card(),
                storeId);
    }
}
