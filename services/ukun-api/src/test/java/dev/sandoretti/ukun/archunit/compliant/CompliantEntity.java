package dev.sandoretti.ukun.archunit.compliant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * The counterpart of {@code violations/}: everything here is written the way this repo requires, so
 * {@code ArchitectureRulesSelfTest} can prove the seven rules stay quiet over correct code. A rule
 * that only ever fires is as useless as one that never does.
 */
@Entity
public class CompliantEntity {

    @Id
    private Long id;

    private String name;

    private BigDecimal price;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CompliantEntity entity && Objects.equals(id, entity.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "CompliantEntity[id=" + id + "]";
    }
}
