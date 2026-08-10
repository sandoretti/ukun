package dev.sandoretti.ukun.auth.domain;

import dev.sandoretti.ukun.auth.domain.Role;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * The identity row. Deliberately anaemic (ADR-0004): a credential, a flag and the roles.
 *
 * <p>{@code @Getter @Setter} and never {@code @Data} — the hard rule of {@code CLAUDE.md}, with two
 * distinct reasons that both bit this repo: a generated mutual {@code toString} produced a real
 * {@code StackOverflowError} in the legacy, and a generated one here would print
 * {@code passwordHash} into every log line that touched an account. The {@code toString} below is
 * the countermeasure, not an afterthought.
 *
 * <p>The id is assigned, not generated: it is a UUID chosen by this service when it reserves the
 * account, and it travels outside the database as the {@code sub} claim.
 */
@Entity
@Table(name = "account")
@Getter
@Setter
public class Account {

    @Id
    private UUID id;

    /**
     * The column is {@code CITEXT}, so {@code where email = ?} is already case-insensitive and no
     * derived query can skip a {@code lower()} nobody remembered to write (docs/10 §2).
     */
    @Column(nullable = false, columnDefinition = "citext")
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /**
     * False until the signup saga activates the account. The database default says the same thing,
     * which is what stops "reserved first" from depending on every caller remembering it (skill
     * {@code spring-auth}, rule 4).
     */
    @Column(nullable = false)
    private boolean enabled;

    /**
     * Roles as an element collection rather than a second entity: {@code account_role} has no
     * identity of its own — it is a composite key over the account and the role, and nothing ever
     * references a row of it.
     *
     * <p>Lazy, and fetched with an {@code @EntityGraph} by the one query that needs it. Marking it
     * eager would be the shortcut that turns every unrelated account read into a join
     * (docs/03-analisis-y-patrones.md §2, reading ladder).
     */
    @ElementCollection
    @CollectionTable(name = "account_role", joinColumns = @JoinColumn(name = "account_id"))
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new LinkedHashSet<>();

    /** Written by the column default; never by Hibernate, hence {@code insertable = false}. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false)
    private Instant updatedAt;

    @PreUpdate
    void stampUpdatedAt() {
        this.updatedAt = Instant.now();
    }

    /**
     * By id, and only by id. A transient account (id still null) is equal to nothing but itself,
     * which is the behaviour a generated {@code equals} over every field gets wrong the moment two
     * unsaved instances happen to carry the same email.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Account account)) {
            return false;
        }
        return id != null && id.equals(account.id);
    }

    /**
     * Constant, on purpose. Deriving it from the id breaks the contract for an entity that is added
     * to a set before being persisted and receives its id afterwards.
     */
    @Override
    public int hashCode() {
        return Objects.hash(Account.class);
    }

    /** No {@code passwordHash}, ever. {@code docs/11-observabilidad-y-logging.md} §4. */
    @Override
    public String toString() {
        return "Account[id=%s, email=%s, enabled=%s]".formatted(id, email, enabled);
    }
}
