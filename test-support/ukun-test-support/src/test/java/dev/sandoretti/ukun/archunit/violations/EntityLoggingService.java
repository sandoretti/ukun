package dev.sandoretti.ukun.archunit.violations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Breaks {@link dev.sandoretti.ukun.archunit.HardRules#ENTITIES_NEVER_REACH_THE_LOG} on purpose,
 * once per shape the compiler produces. The three methods below are not stylistic variants: each
 * compiles to different bytecode, and a rule that only understood one of them would have missed
 * two of the three real defects (#10, #20a, #20b) it exists to catch.
 */
@Service
public class EntityLoggingService {

    private static final Logger log = LoggerFactory.getLogger(EntityLoggingService.class);

    /** {@code info(String, Object)}: the entity is handed over as the argument itself. */
    public void logsTheEntityAsASingleArgument(LoggedEntity entity) {
        log.info("saved {}", entity);
    }

    /** {@code info(String, Object...)}: the entity travels inside a varargs array. */
    public void logsTheEntityInsideAVarargsArray(LoggedEntity entity) {
        log.warn("saved {} at {} in {}", entity, "now", "here");
    }

    /** {@code invokedynamic} on {@code StringConcatFactory}: the entity is stringified first. */
    public void logsTheEntityByConcatenation(LoggedEntity entity) {
        log.error("saved " + entity);
    }
}
