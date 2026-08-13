package dev.sandoretti.ukun.api.catalog;

import dev.sandoretti.ukun.api.catalog.domain.Product;
import dev.sandoretti.ukun.api.catalog.domain.ProductType;
import java.math.BigDecimal;
import java.util.List;

/**
 * Object mother for the catalog, per the {@code testing} skill: named cases instead of anonymous
 * rows, so a test reads as the situation it is about rather than as a list of column values.
 *
 * <p>Two of these exist to reproduce a specific defect and are not interchangeable with any other
 * product:
 *
 * <ul>
 *   <li>{@link #leatherSofa()} and {@link #bunkBed()} both cost {@code 399.99}. That tie is real in
 *       the seed catalogue ({@code Sofá de cuero} and {@code Litera infantil}) and it is what makes
 *       {@code ORDER BY price} without a tie-breaker return unstable pages — defect #23.
 *   <li>{@link #coffeeTable()} and {@link #floorLamp()} carry accents that a customer will not type.
 *       Searching {@code "cafe"} or {@code "lampara"} has to find them — defect #22.
 * </ul>
 *
 * <p>Public, like {@code StoreMother} and {@code CustomerMother}, because the catalogue is not only the
 * catalogue's business: {@code CartIT} prices its lines with these very products, and a suite that
 * inserted four rows of its own would be maintaining a second, drifting copy of this one — the
 * scattered literals the {@code testing} skill rules out.
 */
public final class ProductMother {

    private ProductMother() {}

    public static ProductType type(String name) {
        ProductType type = new ProductType();
        type.setName(name);
        return type;
    }

    /**
     * The three types and five products the catalogue fixtures seed, in an order that is safe to
     * persist: every type comes before the products that point at it.
     *
     * <p>It lives here because two fixtures need the same catalogue for different reasons —
     * {@code CatalogIntegrationTest} reads it through the public endpoints, {@code
     * BackOfficeIntegrationTest} edits and deletes it — and the cases the individual mothers encode
     * (the price tie of defect #23, the accents of #22, the discontinued product of ADR-0013) are only
     * worth anything if both suites see the same ones. Two copies of this list would drift the first
     * time somebody added a sixth product to one of them.
     *
     * <p>It builds and does not persist, so the mother stays a pure builder: the caller decides the
     * transaction, which is what keeps it usable outside a Spring context.
     */
    public static List<Object> demoCatalogue() {
        ProductType sofas = type("Sofás");
        ProductType beds = type("Camas");
        ProductType tables = type("Mesas");

        return List.of(
                sofas,
                beds,
                tables,
                leatherSofa(sofas),
                bunkBed(beds),
                coffeeTable(tables),
                floorLamp(tables),
                discontinuedChair(tables));
    }

    /** {@code 399.99} — one half of the price tie of defect #23. */
    public static Product leatherSofa(ProductType type) {
        return product("SOFA-CUERO", "Sofá de cuero", "399.99", type).imageKey("sofa-cuero.jpg").build();
    }

    /** {@code 399.99} — the other half of the tie, under a different type. */
    public static Product bunkBed(ProductType type) {
        return product("LITERA-INFANTIL", "Litera infantil", "399.99", type).build();
    }

    /** Accented name, cheap. The product {@code "cafe"} must find (defect #22). */
    public static Product coffeeTable(ProductType type) {
        return product("MESA-CAFE", "Mesa de café", "129.75", type).imageKey("mesa-cafe.jpg").build();
    }

    /** Accented name, cheapest of the active ones — the lower bound of the price range tests. */
    public static Product floorLamp(ProductType type) {
        return product("LAMPARA-PIE", "Lámpara de pie", "49.90", type).build();
    }

    /** Discontinued (ADR-0013): it exists in the table and must never reach the public catalogue. */
    public static Product discontinuedChair(ProductType type) {
        return product("SILLA-RETIRADA", "Silla retirada", "19.99", type)
                .active(false)
                .build();
    }

    /**
     * A product that exists only to make a batch bigger, numbered so that {@code n} of them are
     * {@code n} distinct rows.
     *
     * <p>The one case where the mother deliberately does not name a case: a statement-count assertion
     * needs a cart of ten lines and does not care what is in them, and naming ten pieces of furniture
     * would suggest a meaning none of them has. The name says what it is for, which is the part that
     * matters when the test is read later.
     */
    public static Product filler(int index, ProductType type) {
        return product("FILLER-%d".formatted(index), "Producto de relleno %d".formatted(index), "10.00", type)
                .build();
    }

    private static Builder product(String sku, String name, String price, ProductType type) {
        return new Builder(sku, name, price, type);
    }

    /** Only the fields a test ever varies. Everything else has one sensible value and stays out of the way. */
    static final class Builder {

        private final Product product = new Product();

        private Builder(String sku, String name, String price, ProductType type) {
            product.setSku(sku);
            product.setName(name);
            product.setDescription(name + ", for testing purposes.");
            product.setPrice(new BigDecimal(price));
            product.setProductType(type);
        }

        Builder imageKey(String imageKey) {
            product.setImageKey(imageKey);
            return this;
        }

        Builder active(boolean active) {
            product.setActive(active);
            return this;
        }

        Product build() {
            return product;
        }
    }
}
