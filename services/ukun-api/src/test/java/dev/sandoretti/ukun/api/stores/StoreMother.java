package dev.sandoretti.ukun.api.stores;

import dev.sandoretti.ukun.api.stores.domain.Store;
import dev.sandoretti.ukun.api.stores.domain.StoreAddress;

/**
 * Object mother for the store network, per the {@code testing} skill: named cases instead of
 * anonymous rows, so a test reads as the situation it is about.
 *
 * <p>The four stores mirror the seeded ones, and three details of them are load-bearing rather than
 * decorative:
 *
 * <ul>
 *   <li>{@link #onlineStore()} is the only one with {@code isOnline} set. That is a database
 *       invariant ({@code ux_store_online}), so a fixture with two of them would not even insert.
 *   <li>{@link #alcalaStore()} and {@link #torrejonStore()} live in cities spelled with an accent
 *       nobody types into a search box — defect #22.
 *   <li>Names and cities disagree about alphabetical order on purpose: sorted by name the online
 *       store comes last, sorted by city it would not. A fixture that agreed with itself would pass
 *       against a listing sorted by the wrong column.
 * </ul>
 *
 * <p>Public because the availability tests of the {@code inventory} module need a store to ask about;
 * the stock of a store is not a thing that can be set up without one.
 */
public final class StoreMother {

    private StoreMother() {}

    /** The only store orders are served from (RF-04). Alphabetically last of the four. */
    public static Store onlineStore() {
        return store("Ukun Online", "+34910000001", true, address("Calle Mayor, 1", "Madrid", "Madrid", "28013"));
    }

    /** Accented city: searching {@code "alcala"} has to find it (defect #22). */
    public static Store alcalaStore() {
        return store(
                "Ukun Alcalá de Henares",
                "+34910000002",
                false,
                address("Vía Complutense, 1", "Alcalá de Henares", "Madrid", "28801"));
    }

    /** The other accented city, and the one the city filter is asserted against. */
    public static Store torrejonStore() {
        return store(
                "Ukun Torrejón de Ardoz",
                "+34910000003",
                false,
                address("Avenida de la Constitución, 12", "Torrejón de Ardoz", "Madrid", "28850"));
    }

    /** No accent anywhere, which is what makes it the control case of the search tests. */
    public static Store barcelonaStore() {
        return store(
                "Ukun Barcelona",
                "+34930000004",
                false,
                address("Avenida Diagonal, 4", "Barcelona", "Barcelona", "08019"));
    }

    private static Store store(String name, String phone, boolean online, StoreAddress address) {
        Store store = new Store();
        store.setName(name);
        store.setPhone(phone);
        store.setOnline(online);
        store.setAddress(address);

        return store;
    }

    /**
     * Postal codes are real ones for their city. {@code ck_address_postal_code} only checks five
     * digits, so a wrong one would insert happily — the seed data has two of those, corrected on the
     * way in, and there is no reason to reintroduce them here.
     */
    private static StoreAddress address(String street, String city, String province, String postalCode) {
        StoreAddress address = new StoreAddress();
        address.setStreet(street);
        address.setCity(city);
        address.setProvince(province);
        address.setPostalCode(postalCode);

        return address;
    }
}
