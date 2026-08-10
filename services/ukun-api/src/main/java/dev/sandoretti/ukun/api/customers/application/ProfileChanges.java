package dev.sandoretti.ukun.api.customers.application;

import dev.sandoretti.ukun.api.customers.domain.Customer;
import dev.sandoretti.ukun.api.customers.domain.CustomerAddress;
import dev.sandoretti.ukun.api.customers.domain.CustomerCard;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Which fields of a profile a request actually changes, by name.
 *
 * <p>It answers two questions with one comparison, which is why it is a class of its own rather than a
 * flag inside the service. RF-05 asks that a form saved without edits be reported without persisting
 * anything, and {@code docs/11-observabilidad-y-logging.md} §3 asks that {@code profile.updated} carry
 * {@code changedFields}. Leaving the first to Hibernate's dirty checking would answer neither: it
 * decides after the entity has already been written to, and it cannot name what it found.
 *
 * <p><strong>Names, never values.</strong> The list this returns goes straight into a log line, and a
 * profile is personal data — {@code docs/11} §3 contrasts it explicitly with
 * {@code product_type.updated}, the one event allowed to record the previous text of a field, because a
 * type name is not personal data and an address is.
 *
 * <p>The nested objects are compared whole and reported as {@code address} and {@code card}, the names
 * the contract gives them. A per-column breakdown would say which line of somebody's address they
 * corrected, which is more than an audit trail of "the profile changed" needs to know.
 *
 * <p>Pure, static and free of Spring on purpose: it is the one piece of this slice with branches worth
 * testing exhaustively, and it costs no container to do it.
 */
final class ProfileChanges {

    private ProfileChanges() {}

    static List<String> between(Customer customer, UpdateProfileRequest request) {
        List<String> changed = new ArrayList<>();

        if (!Objects.equals(customer.getName(), request.name())) {
            changed.add("name");
        }
        if (!Objects.equals(customer.getLastName(), request.lastName())) {
            changed.add("lastName");
        }
        if (!Objects.equals(customer.getLocale(), request.locale().code())) {
            changed.add("locale");
        }
        if (!sameAddress(customer.getAddress(), request.address())) {
            changed.add("address");
        }
        if (!sameCard(customer.getCard(), request.card())) {
            changed.add("card");
        }
        if (!Objects.equals(customer.getFavoriteStoreId(), request.favoriteStoreId())) {
            changed.add("favoriteStoreId");
        }

        return List.copyOf(changed);
    }

    private static boolean sameAddress(CustomerAddress stored, ProfileAddress requested) {
        return Objects.equals(stored.getStreet(), requested.street())
                && Objects.equals(stored.getCity(), requested.city())
                && Objects.equals(stored.getProvince(), requested.province())
                && Objects.equals(stored.getPostalCode(), requested.postalCode());
    }

    private static boolean sameCard(CustomerCard stored, ProfileCard requested) {
        return Objects.equals(stored.getLast4(), requested.last4())
                && stored.getBrand() == requested.brand()
                && stored.getExpMonth() == requested.expMonth()
                && stored.getExpYear() == requested.expYear();
    }
}
