package dev.sandoretti.ukun.api.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sandoretti.ukun.api.AbstractIntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The guarantee ADR-0014 exists for, asserted directly: <strong>a change that rolls back leaves no
 * audit of having happened.</strong>
 *
 * <p>Every other audit suite exercises the trail through an endpoint, which is the right way to check
 * <em>what</em> a row says. None of them can check this, and the reason is worth stating: to see the
 * row disappear you need an operation that writes it and then fails, and there is no request that
 * produces one — the services are written so that the audited event is the last thing they do. So this
 * class drives {@link AuditTrail} itself inside a transaction it then rolls back, which is the same
 * thing the runtime does when an endpoint throws after the event.
 *
 * <p>What it really pins down is the propagation: {@code REQUIRED} joins the caller's transaction. An
 * implementation that opened {@code REQUIRES_NEW} —an easy and plausible "make the audit robust"
 * change— would commit the row anyway and pass every other test in this project. This is the one that
 * would fail, which is why it is worth a database of its own.
 */
@SpringBootTest(properties = "spring.liquibase.contexts=prod")
class AuditTrailTransactionIT extends AbstractIntegrationTest {

    /** An account and a role: this class never goes through a token, so it states them. */
    private static final Actor ACTOR = new Actor("3f6b2a10-0000-4000-8000-00000000000a", "ADMIN");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        useDatabase(registry, AUDIT_DATABASE);
    }

    @Autowired
    private AuditTrail auditTrail;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearTheTrail() {
        AuditRows.clear(jdbc);
    }

    @Test
    @DisplayName("a row written inside a transaction that rolls back is not there afterwards")
    void rollsBackWithTheChangeItAudits() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            auditTrail.record(AuditedEvent.PRODUCT_DELETED, ACTOR, 42L, Map.of("productId", 42L));
            status.setRollbackOnly();
        });

        assertThat(AuditRows.count(jdbc)).isZero();
    }

    /** The other half of the same statement, so the first one cannot pass by writing nothing ever. */
    @Test
    @DisplayName("a row written inside a transaction that commits is there afterwards")
    void commitsWithTheChangeItAudits() {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> auditTrail.record(AuditedEvent.PRODUCT_DELETED, ACTOR, 42L, Map.of("productId", 42L)));

        assertThat(AuditRows.only(jdbc).resourceId()).isEqualTo("42");
    }

    /**
     * With no ambient transaction the call opens its own and commits — the case of
     * {@code signup.completed}, whose saga cannot hold a transaction across two HTTP calls.
     */
    @Test
    @DisplayName("with no transaction around it, the row is written on its own")
    void writesOutsideAnyTransaction() {
        auditTrail.record(AuditedEvent.SIGNUP_COMPLETED, ACTOR, 7L, Map.of("customerId", 7L));

        AuditRows.Row row = AuditRows.only(jdbc);

        assertThat(row.action()).isEqualTo("SIGNUP_COMPLETED");
        assertThat(row.resourceType()).isEqualTo("customer");
        assertThat(row.actorAccountId()).isEqualTo(UUID.fromString(ACTOR.accountId()));
        assertThat(row.actorRole()).isEqualTo("ADMIN");
    }

    /**
     * Both actor columns are {@code NOT NULL}, and the call that resolves the actor from the request
     * finds nothing when there is no security context — a test, or an endpoint that lost its filter.
     * Without the guard the failure is a {@code NullPointerException} out of {@code UUID.fromString}
     * that names neither the event nor the reason.
     */
    @Test
    @DisplayName("auditing with no actor to name fails saying so")
    void refusesToAuditWithoutAnActor() {
        assertThatThrownBy(() -> auditTrail.record(AuditedEvent.PRODUCT_CREATED, 1L, Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("product.created");

        assertThat(AuditRows.count(jdbc)).isZero();
    }

    /**
     * {@code resource_id} is {@code VARCHAR(64)} so that one column addresses both the numeric ids of
     * the catalogue and the UUIDs of an account ({@code docs/11} §2). Asserted because the alternative
     * —a column per kind, or a numeric one— is what a reader of the entity might otherwise assume.
     */
    @Test
    @DisplayName("a UUID and a number both fit the same resource column")
    void addressesBothKindsOfIdentifier() {
        UUID account = UUID.randomUUID();

        auditTrail.record(AuditedEvent.SIGNUP_COMPLETED, ACTOR, account, Map.of());
        auditTrail.record(AuditedEvent.PRODUCT_CREATED, ACTOR, 1L, Map.of());

        assertThat(AuditRows.all(jdbc))
                .extracting(AuditRows.Row::resourceId)
                .containsExactly(account.toString(), "1");
    }
}
