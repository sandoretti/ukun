package dev.sandoretti.ukun.api.customers;

import dev.sandoretti.ukun.api.customers.domain.CardBrand;
import dev.sandoretti.ukun.api.customers.domain.Customer;
import dev.sandoretti.ukun.api.customers.domain.CustomerAddress;
import dev.sandoretti.ukun.api.customers.domain.CustomerCard;
import java.util.UUID;

/**
 * Customers with their address and their card, built explicitly so each test says what it is about
 * (skill {@code testing}).
 *
 * <p>Two people who share <strong>no</strong> field value: it is what lets a test assert that the
 * profile served is the token holder's and not merely "a profile that looks right". If Ada and Grace
 * lived in the same city, the assertion that proves the ownership rule would still pass with the rule
 * broken.
 *
 * <p>No {@code locale} is set here, and it cannot be: the column is {@code insertable = false} because
 * the {@code DEFAULT} decides the initial value ({@link Customer}). A test that needs another language
 * asks {@code ProfileIntegrationTest} to change it after the insert, which is also how it happens in
 * production — through {@code PUT /api/me/profile}.
 */
public final class CustomerMother {

    private CustomerMother() {}

    public static Customer ada(UUID accountId) {
        return customer(
                accountId,
                "Ada",
                "Lovelace",
                "ada@ukun.dev",
                address("Calle Mayor 1", "Alcalá de Henares", "Madrid", "28801"),
                card("4242", CardBrand.VISA, (short) 12, (short) 2030));
    }

    public static Customer grace(UUID accountId) {
        return customer(
                accountId,
                "Grace",
                "Hopper",
                "grace@ukun.dev",
                address("Avinguda Diagonal 200", "Barcelona", "Barcelona", "08018"),
                card("1881", CardBrand.MASTERCARD, (short) 6, (short) 2029));
    }

    /**
     * The {@code index}-th of a crowd of interchangeable buyers, for the tests that need more customers
     * than there are people worth naming — today only {@code CheckoutConcurrencyIT}, where what matters
     * is that eight distinct accounts contend for one unit.
     *
     * <p><strong>It is the opposite of {@link #ada} and {@link #grace} and must not be used in their
     * place.</strong> Those two share no field value precisely so that a test can tell "the profile of
     * the token holder" from "a profile that looks right"; these deliberately share everything except
     * the account and the email, because a test that asserts anything about a buyer's address has
     * picked the wrong mother.
     *
     * <p>The address and the card are one shape reused, and a shape that is neither Ada's nor Grace's:
     * a crowd that happened to live at Ada's address would make an ownership assertion pass for the
     * wrong reason if one ever pointed here by mistake.
     *
     * <p>{@code email} carries the index only so a failure message names a row. The column is
     * {@code CITEXT NOT NULL} and deliberately <em>not</em> unique (baseline {@code 001-esquema-inicial},
     * with the reason written there): it is a projection, not an identity. {@code account_id} is what is
     * unique, and it comes from the caller.
     */
    public static Customer buyer(UUID accountId, int index) {
        return customer(
                accountId,
                "Buyer",
                "Number " + index,
                "buyer%d@ukun.dev".formatted(index),
                address("Gran Vía 7", "Madrid", "Madrid", "28013"),
                card("9000", CardBrand.VISA, (short) 3, (short) 2031));
    }

    private static Customer customer(
            UUID accountId, String name, String lastName, String email, CustomerAddress address, CustomerCard card) {

        Customer customer = new Customer();
        customer.setAccountId(accountId);
        customer.setName(name);
        customer.setLastName(lastName);
        customer.setEmail(email);
        customer.setAddress(address);
        customer.setCard(card);

        return customer;
    }

    private static CustomerAddress address(String street, String city, String province, String postalCode) {
        CustomerAddress address = new CustomerAddress();
        address.setStreet(street);
        address.setCity(city);
        address.setProvince(province);
        address.setPostalCode(postalCode);

        return address;
    }

    /** Masked when it is created, because there is no unmasked form of it anywhere ({@code docs/08} §5). */
    private static CustomerCard card(String last4, CardBrand brand, short expMonth, short expYear) {
        CustomerCard card = new CustomerCard();
        card.setLast4(last4);
        card.setBrand(brand);
        card.setExpMonth(expMonth);
        card.setExpYear(expYear);

        return card;
    }
}
