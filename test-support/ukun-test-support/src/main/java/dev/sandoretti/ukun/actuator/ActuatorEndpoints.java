package dev.sandoretti.ukun.actuator;

import java.util.List;

/**
 * The Actuator endpoints that must not answer, in any of the three services.
 *
 * <p>Here for the same reason {@code tracing/Traceparent} is: the three {@code ActuatorExposureIT}
 * are only talking about the same fact while they check the same list. Three copies would drift, and
 * they would drift in the direction that costs — somebody opens one endpoint for an afternoon of
 * debugging, updates the test next to it, and the other two services keep asserting about the list as
 * it was.
 *
 * <p>Every entry is here on its own merit rather than for completeness. {@code env} and
 * {@code configprops} print every resolved property, which in this system means the datasource URL
 * and the two OAuth2 client secrets; {@code heapdump} hands over the whole process memory, tokens
 * included; {@code loggers} is writable, so it can turn on the Spring Security DEBUG that
 * {@code docs/11-observabilidad-y-logging.md} §4 forbids in production; {@code beans} and
 * {@code mappings} map the attack surface; and {@code metrics} is the one that turns from harmless to
 * revealing the moment somebody asks for a tag.
 *
 * <p>What it is <strong>not</strong> is the exhaustive list of Actuator endpoints. It cannot be: the
 * set grows with every starter added. The guarantee comes from {@code include: health} in the default
 * document, and this list is what makes a regression of that setting fail a test instead of waiting
 * to be noticed.
 */
public final class ActuatorEndpoints {

    /** Read by the three {@code ActuatorExposureIT} through {@code @FieldSource}. */
    public static final List<String> SENSITIVE = List.of(
            "/actuator/env",
            "/actuator/beans",
            "/actuator/configprops",
            "/actuator/metrics",
            "/actuator/heapdump",
            "/actuator/loggers",
            "/actuator/mappings");

    private ActuatorEndpoints() {}
}
