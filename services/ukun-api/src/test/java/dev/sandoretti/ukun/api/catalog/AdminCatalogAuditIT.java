package dev.sandoretti.ukun.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.sandoretti.ukun.api.audit.AuditRows;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * What the admin half of the catalogue leaves in {@code audit_log} (RF-10, RF-13, ADR-0014): the eight
 * audited events of the {@code catalog} module.
 *
 * <p>Eight out of nine — {@code product.deletion.rejected} and {@code product_type.deletion.rejected}
 * are in the registry of {@code docs/11} §3 without a tick, because a refusal changed nothing. Both
 * negative cases are asserted here, since "the trail records changes, not requests" is the property
 * that separates this table from a request log.
 */
class AdminCatalogAuditIT extends BackOfficeIntegrationTest {

    private static final String ADMIN_PRODUCTS = "/api/admin/products";
    private static final String ONE_PRODUCT = ADMIN_PRODUCTS + "/{id}";
    private static final String PRODUCT_TYPES = "/api/admin/product-types";
    private static final String ONE_TYPE = PRODUCT_TYPES + "/{id}";

    @BeforeEach
    void clearTheTrail() {
        AuditRows.clear(jdbc);
    }

    @Test
    @DisplayName("creating a product records PRODUCT_CREATED against its own id")
    void auditsACreatedProduct() {
        restTestClient
                .post()
                .uri(ADMIN_PRODUCTS)
                .header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                        """
                        {"sku": "BUTACA-AUD", "name": "Butaca de lectura", "description": "Una butaca.",
                         "price": "249.90", "typeId": %d}
                        """
                                .formatted(typeIdOf(SOFAS)))
                .exchange()
                .expectStatus()
                .isCreated();

        AuditRows.Row row = AuditRows.only(jdbc);

        assertThat(row.action()).isEqualTo("PRODUCT_CREATED");
        assertThat(row.resourceType()).isEqualTo("product");
        assertThat(row.resourceId()).isEqualTo(String.valueOf(productIdOf("BUTACA-AUD")));
        assertThat(row.actorAccountId()).isEqualTo(ADMIN_ACCOUNT);
        assertThat(row.actorRole()).isEqualTo("ADMIN");
    }

    /**
     * The names of what changed, never the values — the rule of {@code docs/11} §4 that this event
     * follows even though a price is not a secret: the question the trail answers is what was touched,
     * and a row repeating every edited value grows into a second copy of the table.
     */
    @Test
    @DisplayName("editing a product records which fields changed, not what they now say")
    void auditsAnEditedProduct() {
        long id = productIdOf("MESA-CAFE");

        restTestClient
                .put()
                .uri(ONE_PRODUCT, id)
                .header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                        """
                        {"name": "Mesa de café renovada", "description": "Otra descripción",
                         "price": "149.00", "typeId": %d, "sku": "OTRO-SKU", "isActive": false}
                        """
                                .formatted(typeIdOf(SOFAS)))
                .exchange()
                .expectStatus()
                .isOk();

        AuditRows.Row row = AuditRows.only(jdbc);

        assertThat(row.action()).isEqualTo("PRODUCT_UPDATED");
        assertThat(row.resourceId()).isEqualTo(String.valueOf(id));
        assertThat(AuditRows.detailsOf(row)).containsKey("changedFields");
        assertThat(row.details()).doesNotContain("Mesa de café renovada").doesNotContain("149.00");
    }

    @Test
    @DisplayName("deleting a product records PRODUCT_DELETED")
    void auditsADeletedProduct() {
        long id = productIdOf("LAMPARA-PIE");

        restTestClient
                .delete()
                .uri(ONE_PRODUCT, id)
                .header("Authorization", admin())
                .exchange()
                .expectStatus()
                .isNoContent();

        assertThat(AuditRows.only(jdbc).action()).isEqualTo("PRODUCT_DELETED");
    }

    /**
     * Discontinuing and reactivating are the reversible pair of ADR-0013, and each has its own row —
     * which is the point of auditing them separately rather than as one {@code PRODUCT_UPDATED} with a
     * flag in {@code details}.
     */
    @Test
    @DisplayName("discontinuing and reactivating record one row each, in order")
    void auditsTheReversiblePair() {
        long id = productIdOf("MESA-CAFE");

        restTestClient
                .post()
                .uri(ONE_PRODUCT + ":discontinue", id)
                .header("Authorization", admin())
                .exchange()
                .expectStatus()
                .isOk();

        restTestClient
                .post()
                .uri(ONE_PRODUCT + ":reactivate", id)
                .header("Authorization", admin())
                .exchange()
                .expectStatus()
                .isOk();

        assertThat(AuditRows.all(jdbc))
                .extracting(AuditRows.Row::action)
                .containsExactly("PRODUCT_DISCONTINUED", "PRODUCT_REACTIVATED");
    }

    /**
     * Both operations are idempotent, and the service emits nothing when nothing changed. A screen
     * refreshed twice must not read as two withdrawals in the trail.
     */
    @Test
    @DisplayName("discontinuing twice records one row, not two")
    void doesNotAuditANoOp() {
        long id = productIdOf("MESA-CAFE");

        for (int attempt = 0; attempt < 2; attempt++) {
            restTestClient
                    .post()
                    .uri(ONE_PRODUCT + ":discontinue", id)
                    .header("Authorization", admin())
                    .exchange()
                    .expectStatus()
                    .isOk();
        }

        assertThat(AuditRows.only(jdbc).action()).isEqualTo("PRODUCT_DISCONTINUED");
    }

    @Test
    @DisplayName("creating, renaming and deleting a type record one row each")
    void auditsTheTypeLifecycle() {
        restTestClient
                .post()
                .uri(PRODUCT_TYPES)
                .header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\": \"Estanterías\"}")
                .exchange()
                .expectStatus()
                .isCreated();

        long id = typeIdOf("Estanterías");

        restTestClient
                .put()
                .uri(ONE_TYPE, id)
                .header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\": \"Estantes\"}")
                .exchange()
                .expectStatus()
                .isOk();

        restTestClient
                .delete()
                .uri(ONE_TYPE, id)
                .header("Authorization", admin())
                .exchange()
                .expectStatus()
                .isNoContent();

        assertThat(AuditRows.all(jdbc))
                .extracting(AuditRows.Row::action, AuditRows.Row::resourceType, AuditRows.Row::resourceId)
                .containsExactly(
                        tuple("PRODUCT_TYPE_CREATED", "product_type", String.valueOf(id)),
                        tuple("PRODUCT_TYPE_UPDATED", "product_type", String.valueOf(id)),
                        tuple("PRODUCT_TYPE_DELETED", "product_type", String.valueOf(id)));
    }

    /**
     * The one event that keeps the previous value of a free-text field, and {@code docs/11} §3 says why:
     * renaming a type changes what the whole public catalogue shows, and without the old name the row
     * cannot be read back into what happened. A type name is catalogue data, not personal data — which
     * is what separates this from {@code PROFILE_UPDATED}, three lines of policy away.
     */
    @Test
    @DisplayName("renaming a type keeps both names, unlike every other edit")
    void auditsBothNamesOfARename() {
        long id = typeIdOf(SOFAS);

        restTestClient
                .put()
                .uri(ONE_TYPE, id)
                .header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\": \"Butacas\"}")
                .exchange()
                .expectStatus()
                .isOk();

        assertThat(AuditRows.detailsOf(AuditRows.only(jdbc)))
                .containsEntry("previousName", SOFAS)
                .containsEntry("newName", "Butacas");
    }

    /** A type with products cannot be deleted (RF-13), and a refusal is not a change. */
    @Test
    @DisplayName("a refused type deletion leaves no row")
    void doesNotAuditARefusedTypeDeletion() {
        restTestClient
                .delete()
                .uri(ONE_TYPE, typeIdOf(SOFAS))
                .header("Authorization", admin())
                .exchange()
                .expectStatus()
                .isEqualTo(409);

        assertThat(AuditRows.count(jdbc)).isZero();
    }

    /** Neither does a token without the role: the request never reaches the operation. */
    @Test
    @DisplayName("a customer turned away at the door leaves no row")
    void doesNotAuditARefusedCaller() {
        restTestClient
                .delete()
                .uri(ONE_PRODUCT, productIdOf("LAMPARA-PIE"))
                .header("Authorization", bearer(customerToken(UUID.randomUUID())))
                .exchange()
                .expectStatus()
                .isForbidden();

        assertThat(AuditRows.count(jdbc)).isZero();
    }
}
