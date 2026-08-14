package dev.sandoretti.ukun.api.staff.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Somebody who works in a store (RF-11): who they are, what their job is called, whether they
 * administer the store, and which store it is.
 *
 * <p><strong>{@code accountId} is not a foreign key and never can be</strong>, exactly as in
 * {@code Customer}: the account lives in {@code ukun_auth}, so the reference is an opaque UUID backed
 * by {@code ux_employee_account_id} — the index every authenticated request of this module resolves
 * through, and the way ADR-0006 is enforced in the database
 * ({@code docs/08-identidad-y-seguridad.md} §1).
 *
 * <p>{@code storeId} is a bare {@code Long} and not a {@code Store}: an association would be a
 * compile-time dependency on another module's entity, which {@code docs/14-modules.md} §2 forbids. The
 * foreign key still exists in the schema ({@code fk_employee_store}); what disappears is the
 * {@code @ManyToOne} on this side.
 *
 * <p>{@code isAdmin} is <strong>the only real role inside an employee</strong> (RF-12): not a level in
 * a hierarchy, and not something {@code jobTitle} decides — that one is a label nothing branches on
 * ({@code docs/10-modelo-de-datos.md} §2). Its counterpart is the {@code ADMIN} role of the account in
 * ukun-auth, written by the same saga that writes this row.
 *
 * <p>{@code email} is a <strong>read projection</strong>, not the credential, and deliberately has no
 * {@code UNIQUE}: uniqueness belongs to {@code account.email} in the other database, and a second
 * constraint here would be a second source of truth that the lazy refresh of {@code docs/08} §2 can
 * make diverge. A listing may therefore show a stale address until that employee logs in again — an
 * accepted consequence at the volume of one store.
 *
 * <p>{@code @Getter @Setter} and never {@code @Data}: the hard rule of {@code CLAUDE.md}.
 */
@Entity
@Table(name = "employee")
@Getter
@Setter
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The {@code sub} claim of the token, stored opaquely. See the class javadoc. */
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /** {@code CITEXT} in the schema, so comparisons are case-insensitive without a {@code lower()}. */
    @Column(nullable = false, columnDefinition = "citext")
    private String email;

    @Column(name = "job_title", nullable = false, length = 50)
    private String jobTitle;

    @Column(name = "is_admin", nullable = false)
    private boolean admin;

    /** The store they work in — resolved from the admin who creates them, never from a request. */
    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false)
    private Instant updatedAt;

    @PreUpdate
    void stampUpdatedAt() {
        this.updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Employee employee)) {
            return false;
        }
        return id != null && id.equals(employee.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Employee.class);
    }

    /** No name and no email: {@code docs/11-observabilidad-y-logging.md} §4. */
    @Override
    public String toString() {
        return "Employee[id=%s, accountId=%s, storeId=%s]".formatted(id, accountId, storeId);
    }
}
