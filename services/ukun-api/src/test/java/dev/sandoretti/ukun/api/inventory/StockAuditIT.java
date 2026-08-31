package dev.sandoretti.ukun.api.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sandoretti.ukun.api.audit.AuditRows;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * What the stock screen leaves in {@code audit_log} (RF-09, ADR-0014): the two audited events of the
 * {@code inventory} module, and the three writes that must leave nothing.
 *
 * <p><strong>The negative tests are the point of this class as much as the positive ones.</strong> A
 * trail that records everything is a log with a schema; what makes {@code docs/11} §3 a registry is
 * that {@code stock.optimistic.conflict} and a rejected write are deliberately <em>not</em> in it —
 * nothing changed, so there is nothing to audit. Asserting only the happy paths would pass just as
 * happily against an implementation that wrote a row on every request.
 */
class StockAuditIT extends StockIntegrationTest {

    @BeforeEach
    void clearTheTrail() {
        AuditRows.clear(jdbc);
    }

    @Test
    @DisplayName("creating a line records STOCK_UPDATED against the line, not the product")
    void auditsACreatedLine() {
        long alcala = alcalaId();

        restTestClient
                .post()
                .uri(stockOf(alcala))
                .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken(ANA)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("sku", NEVER_STOCKED, "quantity", 12))
                .exchange()
                .expectStatus()
                .isCreated();

        AuditRows.Row row = AuditRows.only(jdbc);

        assertThat(row.action()).isEqualTo("STOCK_UPDATED");
        assertThat(row.resourceType()).isEqualTo("store_stock");
        // ADR-0015 gave the line its own identity and its own URL; the same product in another store is
        // another resource, so addressing the trail by product would merge two histories into one.
        assertThat(row.resourceId()).isEqualTo(String.valueOf(lineIdOf(NEVER_STOCKED, alcala)));
        assertThat(row.actorAccountId()).isEqualTo(ANA);
        assertThat(row.actorRole()).isEqualTo("EMPLOYEE");
        assertThat(AuditRows.detailsOf(row)).containsKeys("before", "after", "employeeId");
    }

    /** Creating and editing share one event — the registry keys it by what the line ends up at. */
    @Test
    @DisplayName("editing a line records STOCK_UPDATED with the quantity it came from")
    void auditsAnEditedLine() {
        long alcala = alcalaId();
        long line = lineIdOf(STOCKED_SOFA, alcala);

        restTestClient
                .put()
                .uri(lineOf(alcala, line))
                .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken(ANA)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("quantity", 10, "version", 0))
                .exchange()
                .expectStatus()
                .isOk();

        AuditRows.Row row = AuditRows.only(jdbc);

        assertThat(row.action()).isEqualTo("STOCK_UPDATED");
        assertThat(row.resourceId()).isEqualTo(String.valueOf(line));
        // The fixture stocks seven of these, and the write asked for ten.
        assertThat(AuditRows.detailsOf(row)).containsEntry("before", 7).containsEntry("after", 10);
    }

    @Test
    @DisplayName("deleting a line records STOCK_DELETED with the quantity that is gone")
    void auditsADeletedLine() {
        long alcala = alcalaId();
        long line = lineIdOf(STOCKED_SOFA, alcala);

        restTestClient
                .delete()
                .uri(lineOf(alcala, line))
                .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken(ANA)))
                .exchange()
                .expectStatus()
                .isNoContent();

        AuditRows.Row row = AuditRows.only(jdbc);

        assertThat(row.action()).isEqualTo("STOCK_DELETED");
        assertThat(row.resourceId()).isEqualTo(String.valueOf(line));
        // Without it the row cannot say what was lost, which is the whole reason this event is a WARN.
        assertThat(AuditRows.detailsOf(row)).containsEntry("previousQuantity", 7);
    }

    /**
     * The stale write never reaches the change, so it never reaches the trail. This is the assertion
     * that says the row is written by the operation and not by the request.
     */
    @Test
    @DisplayName("a rejected edit leaves no row")
    void doesNotAuditAVersionConflict() {
        long alcala = alcalaId();
        long line = lineIdOf(STOCKED_SOFA, alcala);

        restTestClient
                .put()
                .uri(lineOf(alcala, line))
                .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken(ANA)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("quantity", 99, "version", 7))
                .exchange()
                .expectStatus()
                .isEqualTo(409);

        assertThat(AuditRows.count(jdbc)).isZero();
    }

    /**
     * An employee reaching into another store is turned away before anything happens, and writes
     * nothing — the trail records changes, not attempts. Attempts are what
     * {@code auth.authorization.denied} is for, and that event is deliberately not audited
     * ({@code docs/11} §3).
     *
     * <p>{@code 403} and not {@code 404}: the store in the path is not hers, which
     * {@code requireEmployeeOf} refuses outright. The {@code 404} of this feature is the other case — a
     * line that does not exist <em>in her own</em> store — and it is {@code StockAuthorizationIT}'s.
     */
    @Test
    @DisplayName("a write against someone else's store leaves no row")
    void doesNotAuditAForeignStore() {
        long barcelona = storeIdOf(BARCELONA);
        long barcelonasLine = lineIdOf(ONLY_IN_BARCELONA, barcelona);

        restTestClient
                .delete()
                .uri(lineOf(barcelona, barcelonasLine))
                .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken(ANA)))
                .exchange()
                .expectStatus()
                .isForbidden();

        assertThat(AuditRows.count(jdbc)).isZero();
    }

    /**
     * The bridge of {@code docs/11} §2: from a row of the trail to the full log of the request that
     * caused it.
     *
     * <p>The value comes from the MDC, which Micrometer Tracing fills for every server request —
     * continuing the {@code traceparent} the gateway sends when there is one, and starting a span of its
     * own when there is not, as here. So the column carries the identifier the log lines of that same
     * request carry, which is exactly what makes it a bridge; that the two ends agree across services is
     * {@code TraceCorrelationIT}'s assertion, not this one's.
     *
     * <p>What is asserted is its shape, because the failure this guards against is the fallback of
     * {@code TraceIds.current()} leaking in here — a fresh random identifier per row, well-formed and
     * leading nowhere. Hence {@code currentOrNull}.
     */
    @Test
    @DisplayName("the row carries the trace of the request that caused it")
    void carriesTheTraceOfItsRequest() {
        restTestClient
                .post()
                .uri(stockOf(alcalaId()))
                .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken(ANA)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("sku", NEVER_STOCKED, "quantity", 3))
                .exchange()
                .expectStatus()
                .isCreated();

        assertThat(AuditRows.only(jdbc).traceId()).matches("[0-9a-f]{32}");
    }
}
