package dev.sandoretti.ukun.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Defect #17, as a test: the upload limit of this service and the one the gateway enforces in front of
 * it must be the same number.
 *
 * <p>The legacy has {@code 10MB} in Spring and nothing in nginx, which silently makes the real limit
 * nginx's default of {@code 1MB} — a limit nobody configured, reported by neither, and discovered by
 * uploading a photo. {@code docs/08-identidad-y-seguridad.md} §9 fixes the pair at 5 MB and says the
 * two layers must agree; this is what notices when one of them is changed alone.
 *
 * <p>It reads the two configuration files rather than two application contexts, because that is what the
 * comparison is about: they belong to different services, deployed separately, and nothing at runtime
 * ever sees both. Plain text, no Spring, so it stays in the {@code test} task.
 */
class MultipartLimitTest {

    private static final Path API_CONFIG = Path.of("src/main/resources/application.yaml");
    private static final Path GATEWAY_CONFIG = Path.of("../ukun-gateway/src/main/resources/application.yaml");

    private static final String EXPECTED_LIMIT = "5MB";

    @Test
    @DisplayName("ukun-api and ukun-gateway enforce the same upload limit")
    void bothLayersCarryTheSameLimit() {
        assertThat(valueOf(API_CONFIG, "max-file-size")).isEqualTo(EXPECTED_LIMIT);
        assertThat(valueOf(API_CONFIG, "max-request-size")).isEqualTo(EXPECTED_LIMIT);
        assertThat(valueOf(GATEWAY_CONFIG, "max-body-size")).isEqualTo(EXPECTED_LIMIT);
    }

    /**
     * The first assignment of {@code key} in the file. Both keys appear once in their document, so this
     * is enough — and a YAML parser here would buy nothing but a dependency: what the test compares is a
     * literal, and finding it twice would itself be the problem.
     */
    private static String valueOf(Path config, String key) {
        return read(config)
                .lines()
                .map(String::trim)
                .filter(line -> line.startsWith(key + ":"))
                .map(line -> line.substring(key.length() + 1).trim())
                .findFirst()
                .orElseThrow(() -> new AssertionError("%s declares no %s".formatted(config, key)));
    }

    private static String read(Path config) {
        try {
            return Files.readString(config);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + config.toAbsolutePath(), e);
        }
    }
}
