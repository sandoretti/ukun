package dev.sandoretti.ukun.auth.security;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * The last of the four layers of {@code docs/11-observabilidad-y-logging.md} §4: whatever slips
 * through the first three gets masked on its way into the JSON, so a future accident is a
 * {@code ***} instead of an incident.
 *
 * <p>It is deliberately <strong>not</strong> the main defence. Layer 1 is that the object carrying a
 * secret cannot print it (a hand-written {@code toString()}); layer 2 is the pair of ArchUnit rules
 * that fail the build; layer 3 is the ban on logging request bodies. This one is regex, which is both
 * expensive and blunt: the card-number pattern cannot tell a PAN from an epoch in milliseconds and
 * will mask either. That trade is why it only runs where the value is highest and the cost lowest.
 *
 * <h2>Where it runs, and where it deliberately does not</h2>
 *
 * Registered through {@code logging.structured.json.customizer}, in the same
 * {@code on-profile: "!dev"} document as {@code logging.structured.format.console: ecs}. So it is
 * active exactly where the output is ECS JSON — production — and absent under {@code dev}, where
 * {@code docs/11} §4 is explicit that a badly logged secret has to be visible in order to be fixed.
 * Turning it on locally would hide the very thing layers 1 and 2 exist to catch.
 *
 * <h2>Why a JSON customizer and not the TurboFilter §4 used to name</h2>
 *
 * A {@code TurboFilter} returns a {@code FilterReply}: it decides whether an event is logged and
 * cannot rewrite its message. This is the supported extension point for the format actually in use,
 * and it reaches every String the encoder writes — the message, the stack trace, and the MDC fields
 * {@code BusinessEvents} stamps.
 *
 * <h2>Why this is a copy</h2>
 *
 * Same reasoning as the {@code BusinessEvents} beside it: ADR-0001, no common module. It sits in
 * {@code security/} rather than an {@code observability/} package of its own because that is where
 * this service already keeps its side of {@code docs/11} (ADR-0022 flattened it to layers).
 *
 * <p>It matters most here. This is the service that sees plaintext passwords at all — the login form
 * posts one on every attempt — and defect #10 is exactly a password reaching a log line.
 */
public final class SecretMaskingJsonCustomizer implements StructuredLoggingJsonMembersCustomizer<Object> {

    /** The three patterns of {@code docs/11-observabilidad-y-logging.md} §4, verbatim. */
    private static final List<Masking> SECRETS = List.of(
            new Masking(Pattern.compile("\"password\"\\s*:\\s*\"[^\"]*\""), "\"password\": \"***\""),
            new Masking(Pattern.compile("\\b\\d{13,19}\\b"), "***"),
            new Masking(Pattern.compile("Bearer [A-Za-z0-9._-]+"), "Bearer ***"));

    @Override
    public void customize(JsonWriter.Members<Object> members) {
        members.applyingValueProcessor(JsonWriter.ValueProcessor.of(String.class, SecretMaskingJsonCustomizer::mask));
    }

    /**
     * Package-private so the test can drive the patterns without standing up an encoder: the patterns
     * are what is worth asserting, and they are the part a careless edit breaks.
     */
    static String mask(String value) {
        String masked = value;
        for (Masking secret : SECRETS) {
            masked = secret.pattern().matcher(masked).replaceAll(secret.replacement());
        }
        return masked;
    }

    private record Masking(Pattern pattern, String replacement) {}
}
