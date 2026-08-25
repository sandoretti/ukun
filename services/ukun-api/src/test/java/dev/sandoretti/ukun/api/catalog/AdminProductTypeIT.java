package dev.sandoretti.ukun.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;

/** The back-office CRUD of product types — RF-13, {@code /api/admin/product-types}. */
class AdminProductTypeIT extends BackOfficeIntegrationTest {

    private static final String PRODUCT_TYPES = "/api/admin/product-types";
    private static final String ONE_TYPE = PRODUCT_TYPES + "/{id}";

    private record Type(Long id, String name, long productCount, String createdAt, String updatedAt) {}

    /**
     * {@code Mesas} holds three products and one of them is discontinued ({@code Silla retirada}).
     * <strong>The count includes it</strong>, which is the rule of ADR-0013 and not an oversight: the
     * foreign key that blocks the deletion does not filter by {@code is_active} either, so a count that
     * did would promise a deletion the database refuses.
     */
    @Test
    @DisplayName("the listing counts every product of a type, discontinued ones included")
    void countsDiscontinuedProductsToo() {
        assertThat(typesOf())
                .extracting(Type::name, Type::productCount)
                .containsExactly(tuple(BEDS, 1L), tuple(TABLES, 3L), tuple(SOFAS, 1L));
    }

    /**
     * One statement for the whole listing, not one plus a count per row. Three types today and a shop
     * invents more; the arithmetic version of defect #12 grows with them.
     *
     * <p>Exactly one, not "at most a few": the counter is cleared immediately before the request, so
     * every statement it sees belongs to it.
     */
    @Test
    @DisplayName("the listing costs one statement however many types there are")
    void doesNotCountPerType() {
        var statistics = freshStatistics();

        typesOf();

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a type is created with a count of zero and answers Location")
    void createsAType() {
        String location = restTestClient
                .post()
                .uri(PRODUCT_TYPES)
                .header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\": \"Estanterías\"}")
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.name")
                .isEqualTo("Estanterías")
                .jsonPath("$.productCount")
                .isEqualTo(0)
                .jsonPath("$.createdAt")
                .isNotEmpty()
                .returnResult()
                .getResponseHeaders()
                .getFirst("Location");

        assertThat(location).isEqualTo(PRODUCT_TYPES + "/" + typeIdOf("Estanterías"));
    }

    /**
     * The name is trimmed before it is compared, so a leading space does not smuggle a second
     * {@code Sofás} past {@code ux_product_type_name} — the contract says "trimmed, never blank".
     */
    @Test
    @DisplayName("a duplicate name is a 409 naming the field, not a 500 from the unique index")
    void rejectsADuplicateName() {
        restTestClient
                .post()
                .uri(PRODUCT_TYPES)
                .header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\": \"  Sofás  \"}")
                .exchange()
                .expectStatus()
                .isEqualTo(409)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("DUPLICATE_VALUE")
                .jsonPath("$.resource")
                .isEqualTo("productType")
                .jsonPath("$.field")
                .isEqualTo("name")
                .jsonPath("$.traceId")
                .isNotEmpty();
    }

    @Test
    @DisplayName("renaming a type keeps its own name from clashing with itself")
    void renamesAType() {
        long id = typeIdOf(SOFAS);

        restTestClient
                .put()
                .uri(ONE_TYPE, id)
                .header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\": \"" + SOFAS + "\"}")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.name")
                .isEqualTo(SOFAS)
                .jsonPath("$.productCount")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a type with no products is really deleted")
    void deletesAnEmptyType() {
        restTestClient
                .post()
                .uri(PRODUCT_TYPES)
                .header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\": \"Vacío\"}")
                .exchange()
                .expectStatus()
                .isCreated();

        restTestClient
                .delete()
                .uri(ONE_TYPE, typeIdOf("Vacío"))
                .header("Authorization", admin())
                .exchange()
                .expectStatus()
                .isNoContent();

        assertThat(typesOf()).extracting(Type::name).doesNotContain("Vacío");
    }

    /**
     * The count is what the {@code 409} reports, and the number is three because {@code Mesas} carries a
     * discontinued product as well.
     */
    @Test
    @DisplayName("a type with products answers 409 PRODUCT_TYPE_IN_USE with how many")
    void refusesToDeleteATypeInUse() {
        restTestClient
                .delete()
                .uri(ONE_TYPE, typeIdOf(TABLES))
                .header("Authorization", admin())
                .exchange()
                .expectStatus()
                .isEqualTo(409)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("PRODUCT_TYPE_IN_USE")
                .jsonPath("$.inProducts")
                .isEqualTo(3);
    }

    /**
     * The check in the use case is the experience; <strong>the foreign key is the guarantee</strong>.
     * This is the half that survives the check being removed, a bug in it, or somebody typing the
     * statement straight into {@code psql} — which is exactly what this does.
     */
    @Test
    @DisplayName("a type with products cannot be deleted from the database either")
    void refusesToDeleteATypeInUseEvenFromSql() {
        long id = typeIdOf(TABLES);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM product_type WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("an unknown type is a 404 naming the resource")
    void answers404ForAnUnknownType() {
        restTestClient
                .get()
                .uri(ONE_TYPE, 4242)
                .header("Authorization", admin())
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("RESOURCE_NOT_FOUND")
                .jsonPath("$.resource")
                .isEqualTo("productType");
    }

    /**
     * An employee is not an administrator. The role check is {@code ukun-api}'s own and does not depend
     * on the gateway having done its coarse filtering first ({@code docs/08-identidad-y-seguridad.md}
     * §4).
     */
    @Test
    @DisplayName("an employee without the admin flag gets 403 on the back-office")
    void refusesANonAdminEmployee() {
        restTestClient
                .get()
                .uri(PRODUCT_TYPES)
                .header("Authorization", bearer(employeeToken(UUID.randomUUID())))
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("FORBIDDEN");
    }

    @Test
    @DisplayName("anonymous traffic gets 401, never the listing")
    void refusesAnonymousTraffic() {
        restTestClient
                .get()
                .uri(PRODUCT_TYPES)
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("UNAUTHENTICATED");
    }

    private List<Type> typesOf() {
        return restTestClient
                .get()
                .uri(PRODUCT_TYPES)
                .header("Authorization", admin())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(new ParameterizedTypeReference<List<Type>>() {})
                .returnResult()
                .getResponseBody();
    }
}
