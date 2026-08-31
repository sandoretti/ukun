package dev.sandoretti.ukun.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonWriter;

/**
 * The fourth layer of {@code docs/11-observabilidad-y-logging.md} §4. Two things are worth asserting
 * and they fail differently: that the patterns match what §4 says they match, and that the customizer
 * is actually wired into the JSON writer. The second one is the silent failure — a customizer that
 * registers nothing masks nothing, and produces perfectly valid output while doing it.
 */
class SecretMaskingJsonCustomizerTest {

    @Nested
    @DisplayName("the three patterns of docs/11 §4")
    class Patterns {

        @Test
        @DisplayName("masks a password field, keeping the key so the line still reads")
        void masksAPasswordField() {
            var line = "rejected body {\"email\":\"ada@example.com\",\"password\":\"hunter2\"}";

            var masked = SecretMaskingJsonCustomizer.mask(line);

            assertThat(masked).doesNotContain("hunter2").contains("\"password\": \"***\"", "ada@example.com");
        }

        @Test
        @DisplayName("masks anything shaped like a card number")
        void masksACardNumber() {
            var masked = SecretMaskingJsonCustomizer.mask("card 4111111111111111 declined");

            assertThat(masked).isEqualTo("card *** declined");
        }

        @Test
        @DisplayName("masks a bearer token, keeping the scheme")
        void masksABearerToken() {
            var masked = SecretMaskingJsonCustomizer.mask("Authorization: Bearer eyJhbGci.OiJSUzI1.NiJ9-x_y");

            assertThat(masked).isEqualTo("Authorization: Bearer ***");
        }

        @Test
        @DisplayName("leaves a line with no secret exactly as it was")
        void leavesAnOrdinaryLineAlone() {
            var line = "order.created orderId=42 customerId=7 total=129.90";

            assertThat(SecretMaskingJsonCustomizer.mask(line)).isEqualTo(line);
        }
    }

    @Test
    @DisplayName("reaches every string the encoder writes, not just the message")
    void reachesEveryStringWritten() {
        // Typed on Object, like the Members the logging system hands the customizer at runtime.
        JsonWriter<Object> writer = JsonWriter.of(members -> {
            members.add("message", event -> ((Event) event).message());
            members.add("password", event -> ((Event) event).password());
            new SecretMaskingJsonCustomizer().customize(members);
        });

        var json = writer.writeToString(new Event("Authorization: Bearer abc.def-ghi", "hunter2"));

        assertThat(json).doesNotContain("abc.def-ghi").contains("Bearer ***");
    }

    /** The MDC fields BusinessEvents stamps arrive as members of their own, hence the second one. */
    private record Event(String message, String password) {}
}
