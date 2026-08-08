package dev.sandoretti.ukun.archunit.violations;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

/**
 * Breaks {@link dev.sandoretti.ukun.archunit.HardRules#ENTITIES_WRITE_THEIR_OWN_IDENTITY} on
 * purpose: {@code @Data} generates {@code equals}, {@code hashCode} and {@code toString} over every
 * field. In the legacy that combination produced a real {@code StackOverflowError} between
 * {@code Pedido} and {@code ProductoPedido}, and it is how a password reaches a log line.
 *
 * <p>The annotation itself is invisible to any bytecode tool ({@code SOURCE} retention); what makes
 * this detectable is the {@code @lombok.Generated} that {@code services/lombok.config} asks for.
 */
@Data
@Entity
public class LombokEntity {

    @Id
    private Long id;

    private String password;
}
