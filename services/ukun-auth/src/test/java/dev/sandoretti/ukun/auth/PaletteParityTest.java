package dev.sandoretti.ukun.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The login page's stylesheet copies the shop's palette, and this is what stops the copy from
 * drifting.
 *
 * <p>{@code docs/07-frontend-ux-ui.md} makes {@code _tokens.scss} the single source of the palette,
 * and the reason it is copied rather than read is spelled out at the top of {@code login.css}: the
 * two artefacts are built by different toolchains, and wiring Gradle to npm to save four values
 * would cost the ability to build this service on its own. Duplication a test watches is a different
 * thing from duplication nobody notices — which is exactly the legacy defect {@code docs/07} was
 * correcting: two stylesheets with two greens that had silently diverged.
 *
 * <p>It lives here and not in the frontend suite on purpose: the file that can drift is this
 * service's, so the check has to run when this service is built.
 */
class PaletteParityTest {

    /** From this module's directory up to the repository root. Gradle runs tests from the former. */
    private static final Path TOKENS =
            Path.of("../../apps/ukun-web/src/shared/theme/_tokens.scss").normalize();

    private static final Path STYLESHEET = Path.of("src/main/resources/static/css/login.css");

    /** {@code $name: #rrggbb;} — the only shape the token file uses for a colour. */
    private static final Pattern SCSS_COLOUR = Pattern.compile("\\$([\\w-]+):\\s*(#[0-9a-fA-F]{3,8})\\s*;");

    @Test
    @DisplayName("every colour of the shop's palette is one the login page paints")
    void theLoginPageUsesTheSamePaletteAsTheShop() throws IOException {
        Map<String, String> palette = readPalette();
        String stylesheet = Files.readString(STYLESHEET, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);

        List<String> drifted = palette.entrySet().stream()
                .filter(token -> !stylesheet.contains(token.getValue().toLowerCase(Locale.ROOT)))
                .map(token -> "$%s = %s".formatted(token.getKey(), token.getValue()))
                .toList();

        assertThat(drifted)
                .as(
                        "%s copies the palette of %s. Change the token first, then bring this stylesheet "
                                + "along — its header explains why the two are not one file.",
                        STYLESHEET, TOKENS)
                .isEmpty();
    }

    @Test
    @DisplayName("the token file is where this test expects it, or the check above proves nothing")
    void theTokenFileIsReachable() throws IOException {
        // Without this, renaming or moving _tokens.scss turns the parity check into a test that reads
        // an empty palette, finds nothing missing, and passes for the rest of the project's life.
        assertThat(TOKENS).exists();
        assertThat(readPalette()).isNotEmpty();
    }

    private static Map<String, String> readPalette() throws IOException {
        Matcher matcher = SCSS_COLOUR.matcher(Files.readString(TOKENS, StandardCharsets.UTF_8));

        Map<String, String> palette = new LinkedHashMap<>();
        while (matcher.find()) {
            palette.put(matcher.group(1), matcher.group(2));
        }

        return palette;
    }
}
