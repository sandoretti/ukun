package dev.sandoretti.ukun.archunit.violations;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.Objects;

/**
 * Breaks {@link dev.sandoretti.ukun.archunit.HardRules#ENTITIES_DO_NOT_USE_FLOATING_POINT} on
 * purpose, in both flavours the rule names: the wrapper and the primitive. The legacy declared
 * prices as {@code Float} and lost cents on every multiplication.
 */
@Entity
public class FloatingPointEntity {

    @Id
    private Long id;

    private Double price;

    private float discount;

    public Long getId() {
        return id;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FloatingPointEntity entity && Objects.equals(id, entity.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "FloatingPointEntity[id=" + id + "]";
    }
}
