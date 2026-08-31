package dev.sandoretti.ukun.api.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import dev.sandoretti.ukun.api.AbstractIntegrationTest;

/**
 * The one test that proves the production log format and its secret mask are wired to each other.
 *
 * <p>It has to turn ECS on for itself because the build turns it off for every other test: the test
 * tasks set {@code logging.structured.format.console} to the empty string so a failing build stays
 * readable ({@code ukun-java-conventions.gradle.kts}). That trade leaves exactly one gap — nothing
 * else exercises the format that production actually uses — and this class is what fills it.
 *
 * <p>The failure it exists to catch is the quietest one in this release. If
 * {@code logging.structured.json.customizer} is missing from an {@code application.yaml}, or names a
 * class in another service's package after a copy-paste, ECS keeps working perfectly and layer 3 of
 * {@code docs/11-observabilidad-y-logging.md} §4 simply is not there. No error, no warning, no
 * difference in any other test — just a production log that no longer masks anything.
 *
 * <p>Asserting that something is <em>absent</em> from a log line is the same shape
 * {@code AccessLogWebFilterTest} already uses, and it does not break the rule against asserting on
 * log message contents: what is pinned is the secret not being there, not the wording around it.
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(properties = "logging.structured.format.console=ecs")
class EcsMaskedLoggingIT extends AbstractIntegrationTest {

    /** Sixteen digits: the shape of a PAN, which is what defects #20a and #20b actually leaked. */
    private static final String LOOKS_LIKE_A_PAN = "4111111111111111";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        useDatabase(registry, SCHEMA_ONLY_DATABASE);
    }

    @Test
    @DisplayName("writes ECS json and masks the secret in it, message and MDC alike")
    void writesEcsJsonWithTheSecretMasked(CapturedOutput output) {
        MDC.put("cardNumber", LOOKS_LIKE_A_PAN);
        try {
            LoggerFactory.getLogger("ukun.business").info("card {} declined", LOOKS_LIKE_A_PAN);
        } finally {
            MDC.remove("cardNumber");
        }

        String line = lastLineOf(output, "declined");
        assertThat(line).doesNotContain(LOOKS_LIKE_A_PAN);
        assertThatCode(() -> {
            JsonNode json = new ObjectMapper().readTree(line);
            assertThat(json.path("message").asText()).isEqualTo("card *** declined");
            assertThat(json.path("cardNumber").asText()).isEqualTo("***");
            assertThat(json.path("log").path("level").asText()).isEqualTo("INFO");
        }).doesNotThrowAnyException();
    }

    private static String lastLineOf(CapturedOutput output, String needle) {
        return output.getOut().lines()
                .filter(line -> line.contains(needle))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("no line containing '" + needle + "' was captured"));
    }
}
