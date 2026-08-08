package dev.sandoretti.ukun.archunit.violations;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * A well-formed entity. It exists so the other fixtures have something to return and to log without
 * dragging a second violation into the test, and it doubles as the shape {@code docs/03} §2 asks
 * for: {@code equals}/{@code hashCode} by id, {@code toString} with scalars, money in
 * {@code BigDecimal}.
 *
 * <p>Like everything under this package it must stay outside {@code dev.sandoretti.ukun.api}: a new
 * top-level package there would be an eleventh application module and would break
 * {@code ModulithTest}.
 */
@Entity
public class LoggedEntity {

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
        return other instanceof LoggedEntity entity && Objects.equals(id, entity.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "LoggedEntity[id=" + id + "]";
    }
}
