package dev.sandoretti.ukun.api.customers.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sandoretti.ukun.api.customers.CustomerMother;
import dev.sandoretti.ukun.api.customers.domain.CardBrand;
import dev.sandoretti.ukun.api.customers.domain.Customer;
import dev.sandoretti.ukun.api.customers.domain.ProfileLocale;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What counts as a change to a profile, which decides two things at once: whether {@code PUT
 * /api/me/profile} writes anything (RF-05) and what {@code profile.updated} reports having changed
 * ({@code docs/11-observabilidad-y-logging.md} §3).
 *
 * <p>A unit test with no container and no mocks, because the subject is a pure comparison — the cases
 * worth enumerating are branches, not queries.
 */
class ProfileChangesTest {

    private static final UUID ACCOUNT = UUID.fromString("8f2c1e40-0000-4000-8000-0000000000a1");

    @Test
    @DisplayName("a form saved exactly as it was loaded changes nothing")
    void findsNoChangeInAnUntouchedForm() {
        Customer ada = seeded();

        assertThat(ProfileChanges.between(ada, sameAs(ada))).isEmpty();
    }

    @Test
    @DisplayName("each edited field is reported by name")
    void namesEveryEditedField() {
        Customer ada = seeded();

        UpdateProfileRequest edited = new UpdateProfileRequest(
                "Augusta",
                "King",
                ProfileLocale.EN,
                new ProfileAddress("Carrer de Mallorca 401", "Barcelona", "Barcelona", "08013"),
                new ProfileCard("1234", CardBrand.AMEX, (short) 3, (short) 2031),
                7L);

        assertThat(ProfileChanges.between(ada, edited))
                .containsExactly("name", "lastName", "locale", "address", "card", "favoriteStoreId");
    }

    /**
     * The nested objects are compared whole and reported by their contract name: one edited line of an
     * address is "the address changed", not a log line saying which line of somebody's address it was.
     */
    @Test
    @DisplayName("one edited line of the address reports the address, and nothing else")
    void reportsTheAddressAsAWhole() {
        Customer ada = seeded();
        UpdateProfileRequest request = sameAs(ada);

        UpdateProfileRequest moved = new UpdateProfileRequest(
                request.name(),
                request.lastName(),
                request.locale(),
                new ProfileAddress(
                        "Calle Mayor 2",
                        request.address().city(),
                        request.address().province(),
                        request.address().postalCode()),
                request.card(),
                request.favoriteStoreId());

        assertThat(ProfileChanges.between(ada, moved)).containsExactly("address");
    }

    /** Setting a favourite store and clearing it are both changes; {@code null} is a value here. */
    @Test
    @DisplayName("clearing the favourite store is a change, not the absence of one")
    void treatsAClearedFavouriteStoreAsAChange() {
        Customer ada = seeded();
        ada.setFavoriteStoreId(4L);

        assertThat(ProfileChanges.between(ada, sameAs(ada))).containsExactly("favoriteStoreId");
    }

    @Test
    @DisplayName("a card that expires a month later is a change of card")
    void detectsARenewedCard() {
        Customer ada = seeded();
        UpdateProfileRequest request = sameAs(ada);

        UpdateProfileRequest renewed = new UpdateProfileRequest(
                request.name(),
                request.lastName(),
                request.locale(),
                request.address(),
                new ProfileCard(
                        request.card().last4(),
                        request.card().brand(),
                        (short) (request.card().expMonth() + 1),
                        request.card().expYear()),
                request.favoriteStoreId());

        assertThat(ProfileChanges.between(ada, renewed)).containsExactly("card");
    }

    /**
     * {@link CustomerMother} cannot set the locale — the column is defaulted by the database — so the
     * fixture states here what the {@code DEFAULT} would have written.
     */
    private static Customer seeded() {
        Customer ada = CustomerMother.ada(ACCOUNT);
        ada.setLocale(ProfileLocale.ES.code());

        return ada;
    }

    /** The profile as it stands, in the shape the browser would send it back unedited. */
    private static UpdateProfileRequest sameAs(Customer customer) {
        return new UpdateProfileRequest(
                customer.getName(),
                customer.getLastName(),
                ProfileLocale.of(customer.getLocale()),
                new ProfileAddress(
                        customer.getAddress().getStreet(),
                        customer.getAddress().getCity(),
                        customer.getAddress().getProvince(),
                        customer.getAddress().getPostalCode()),
                new ProfileCard(
                        customer.getCard().getLast4(),
                        customer.getCard().getBrand(),
                        customer.getCard().getExpMonth(),
                        customer.getCard().getExpYear()),
                null);
    }
}
