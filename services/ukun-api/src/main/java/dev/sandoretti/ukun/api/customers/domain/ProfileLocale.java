package dev.sandoretti.ukun.api.customers.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Locale;

/**
 * The language a customer is written to in — the {@code Locale} schema of the contract and the closed
 * set of {@code ck_customer_locale} (ADR-0019).
 *
 * <p>It is the <strong>only</strong> point where the i18n decision touches the backend. Everything
 * else about language lives in {@code apps/ukun-web}: this service emits no user-facing text at all,
 * only machine-readable {@code code}s (ADR-0018, {@code docs/12-error-contract.md}). What this enum
 * carries is a preference that has to survive changing device, which is why it is persisted here
 * rather than left in the browser's storage.
 *
 * <p><strong>Why the entity still stores a {@code String}.</strong> The column is {@code CHAR(2)} with
 * lowercase values, and {@code @Enumerated(STRING)} would write {@code 'ES'} — rejected by the
 * {@code CHECK}. The conversion is two lines in {@link
 * dev.sandoretti.ukun.api.customers.application.ProfileMapper} instead of an {@code AttributeConverter}
 * plus the {@code @JdbcTypeCode(CHAR)} argument {@link Customer} already documents having had with
 * {@code ddl-auto: validate}. The type is closed where it is read and written; the column stays what
 * the baseline made it.
 *
 * <p>{@code @JsonValue}/{@code @JsonCreator} are what put {@code "es"} on the wire from a constant
 * Java can name in upper case. The {@code @Schema} states the same set for springdoc rather than
 * trusting it to follow the Jackson annotations — {@code OpenApiDriftIT} compares enums, and the
 * frontend generates a union type from this one.
 */
@Schema(type = "string", allowableValues = {"es", "en"})
public enum ProfileLocale {
    ES,
    EN;

    /** What the column holds and what the contract publishes: the ISO 639-1 code, lowercase. */
    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * An unknown value is a {@code 400} on the field, not a silent fallback to Spanish: the contract
     * declares a closed enum, and answering a request that asked for a language nobody has by quietly
     * picking one is how a bug becomes invisible.
     */
    @JsonCreator
    public static ProfileLocale of(String code) {
        return valueOf(code.toUpperCase(Locale.ROOT));
    }
}
