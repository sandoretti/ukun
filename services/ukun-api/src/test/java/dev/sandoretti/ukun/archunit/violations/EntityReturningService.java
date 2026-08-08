package dev.sandoretti.ukun.archunit.violations;

import org.springframework.stereotype.Service;

/**
 * Breaks {@link dev.sandoretti.ukun.archunit.HardRules#SERVICES_DO_NOT_RETURN_ENTITIES} on purpose:
 * handing an entity out of a service is how a lazy association ends up serialised into a response,
 * and how the API contract starts tracking the schema. ADR-0009: the return type is a {@code record}
 * mapped with MapStruct.
 */
@Service
public class EntityReturningService {

    public LoggedEntity findTheEntityItself() {
        return new LoggedEntity();
    }
}
