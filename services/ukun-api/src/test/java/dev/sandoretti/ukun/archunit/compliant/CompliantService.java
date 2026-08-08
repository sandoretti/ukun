package dev.sandoretti.ukun.archunit.compliant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Returns a {@code record}, not the entity, and logs scalars pulled out of the entity rather than
 * the entity itself. The three log lines below are the reason the logging rule does not simply flag
 * every method that mentions both an entity and a logger: this is what correct code looks like, and
 * the rule has to stay silent over it.
 */
@Service
public class CompliantService {

    private static final Logger log = LoggerFactory.getLogger(CompliantService.class);

    public ProductView find(CompliantEntity entity) {
        log.info("loaded product {}", entity.getId());
        log.debug("product {} costs {}", entity.getName(), entity.getPrice());
        log.info("loaded product " + entity.getId());

        return new ProductView(entity.getId(), entity.getName());
    }

    public record ProductView(Long id, String name) {}
}
