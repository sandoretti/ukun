package dev.sandoretti.ukun.api.customers;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sandoretti.ukun.api.audit.AuditRows;
import dev.sandoretti.ukun.api.customers.application.CustomerProfileResponse;
import dev.sandoretti.ukun.api.customers.application.ProfileAddress;
import dev.sandoretti.ukun.api.customers.application.ProfileCard;
import dev.sandoretti.ukun.api.customers.application.UpdateProfileRequest;
import dev.sandoretti.ukun.api.customers.domain.ProfileLocale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * What editing a profile leaves in {@code audit_log} (RF-05, ADR-0014), and — the reason this class
 * exists as much as the row itself — <strong>what it must not leave</strong>.
 *
 * <p>{@code profile.updated} is the audited event with the most personal data behind it: an address is
 * PII and a card is PCI, and {@code details} is an open JSONB column. {@code docs/11} §4 resolves it in
 * one line — the row records <em>which</em> fields changed and never their values, because auditing
 * that the address changed is the requirement and auditing what it now says is not. Half of the tests
 * here assert the negative half of that sentence.
 */
class ProfileAuditIT extends ProfileIntegrationTest {

    private static final UUID ADA = UUID.fromString("8f2c1e40-0000-4000-8000-0000000000a1");

    private static final String STREET = "Carrer de Mallorca 401";

    @BeforeEach
    void clearTheTrail() {
        AuditRows.clear(jdbc);
    }

    @Test
    @DisplayName("an edit records PROFILE_UPDATED against the customer, by the customer")
    void auditsAnEditedProfile() {
        seed(CustomerMother.ada(ADA));
        String token = customerToken(ADA, "ada@ukun.dev");

        put(token, edited(profileOf(token)));

        AuditRows.Row row = AuditRows.only(jdbc);

        assertThat(row.action()).isEqualTo("PROFILE_UPDATED");
        assertThat(row.resourceType()).isEqualTo("customer");
        assertThat(row.actorAccountId()).isEqualTo(ADA);
        assertThat(row.actorRole()).isEqualTo("CUSTOMER");
        assertThat(AuditRows.detailsOf(row)).containsKey("changedFields");
    }

    /**
     * The rule of {@code docs/11} §4, asserted against the values that actually travelled: the new
     * street, the new card digits, the new surname. Field names may be in the row; none of these may.
     */
    @Test
    @DisplayName("the audited edit names the fields and never their values")
    void auditsWithoutAnyPersonalValue() {
        seed(CustomerMother.ada(ADA));
        String token = customerToken(ADA, "ada@ukun.dev");

        put(token, edited(profileOf(token)));

        AuditRows.Row row = AuditRows.only(jdbc);

        assertThat(row.details())
                .doesNotContain(STREET)
                .doesNotContain("Barcelona")
                .doesNotContain("1234")
                .doesNotContain("AMEX")
                .doesNotContain("King")
                .doesNotContain("ada@ukun.dev");

        // What it does carry: the names, which is the whole of what the requirement asks for.
        assertThat(String.valueOf(AuditRows.detailsOf(row).get("changedFields")))
                .contains("address")
                .contains("card");
    }

    /**
     * RF-05 answers a form saved without edits with a {@code 200} and writes nothing — so there is
     * nothing to audit either. Without this, a trail of a busy profile screen would be mostly rows
     * saying somebody opened it.
     */
    @Test
    @DisplayName("saving a form with no edits leaves no row")
    void doesNotAuditANoOp() {
        seed(CustomerMother.ada(ADA));
        String token = customerToken(ADA, "ada@ukun.dev");

        put(token, unchanged(profileOf(token)));

        assertThat(AuditRows.count(jdbc)).isZero();
    }

    /**
     * Changing the favourite store is a second event, {@code favorite_store.changed}, and it is
     * deliberately <em>not</em> audited ({@code docs/11} §3): which shop somebody prefers is a
     * convenience of the catalogue, not a change anyone can claim later. So one edit that touches both
     * leaves exactly one row.
     */
    @Test
    @DisplayName("an edit that also changes the favourite store still leaves one row")
    void doesNotAuditTheFavouriteStore() {
        seed(CustomerMother.ada(ADA));
        String token = customerToken(ADA, "ada@ukun.dev");
        CustomerProfileResponse profile = profileOf(token);

        UpdateProfileRequest request = new UpdateProfileRequest(
                profile.name(),
                profile.lastName(),
                profile.locale(),
                profile.address(),
                profile.card(),
                storeIdOf("Ukun Barcelona"));

        put(token, request);

        assertThat(AuditRows.all(jdbc)).extracting(AuditRows.Row::action).containsExactly("PROFILE_UPDATED");
    }

    /** Every field this suite is about, changed at once. */
    private static UpdateProfileRequest edited(CustomerProfileResponse profile) {
        return new UpdateProfileRequest(
                "Augusta",
                "King",
                ProfileLocale.EN,
                new ProfileAddress(STREET, "Barcelona", "Barcelona", "08013"),
                new ProfileCard("1234", profile.card().brand(), (short) 3, (short) 2031),
                profile.favoriteStoreId());
    }

    private static UpdateProfileRequest unchanged(CustomerProfileResponse profile) {
        return new UpdateProfileRequest(
                profile.name(),
                profile.lastName(),
                profile.locale(),
                profile.address(),
                profile.card(),
                profile.favoriteStoreId());
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

    private void put(String token, UpdateProfileRequest request) {
        restTestClient
                .put()
                .uri(MY_PROFILE)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus()
                .isOk();
    }
}
