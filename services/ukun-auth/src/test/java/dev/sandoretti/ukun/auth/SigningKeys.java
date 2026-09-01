package dev.sandoretti.ukun.auth;

/**
 * The RSA key every {@code @SpringBootTest} of this suite signs with.
 *
 * <p>It is here and not in {@link AbstractIntegrationTest} for one reason only: it is 1.6 KB of
 * base64 and that class is meant to be read. {@code AbstractIntegrationTest.SIGNING_KEY} is the name
 * the fixtures use.
 *
 * <p>Since ADR-0024 {@code ukun.signing-key} has no default — the service refuses to start without
 * one — so a context that does not supply it does not start at all. That is stricter than what the
 * two client secrets do, and deliberately: an unresolvable {@code ${...}} inside a
 * {@code @ConfigurationProperties} binds as the literal text, which is why two suites used to boot
 * with the string {@code ${UKUN_OAUTH2_GATEWAY_SECRET}} as a secret. A key cannot do that — it has to
 * parse.
 *
 * <p><strong>A throwaway key generated for this file.</strong> It signs nothing outside the test JVM
 * and validates nothing outside it either. Committing it is not a leak; reusing it anywhere that is
 * not this suite would be.
 */
public final class SigningKeys {

    /**
     * PKCS#8, base64 of the DER — the same shape {@code .env.example} documents. It has to be a
     * compile-time constant because {@code @SpringBootTest(properties = ...)} concatenates it.
     */
    public static final String TEST_KEY =
            "MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQDGWSH/EQJmKiot8k7JD2S47xyAzP15LS3BMt5TtCW3BEAsGEZx"
            + "yOhY4dOXRr2yOXKW87lhTmJ8y/V5HKyq3y8d3KBhlIaOGPUmuAT+OHVR/clOeGNE79DNBLjVP/yjfP40u4R43L5wLxdhgtGSJBvB"
            + "JuqYlnUw3+QyAxufphNfyWfa/IM0nCOARLuGOU6Mut4wE02IGHksoo5/XtNShsOmhhM6m9Xr3VW6kSmf4syTFMXKtb2Nt1ofYkKj"
            + "sptX6zxAlcUoTH6m5cFINp22NF78aHsttaIPvR0vn5JcpPWh7QICcU3xoAqCjUb+7jB4LRWQkNyZk6CmxMqx1+v0DrcxAgMBAAEC"
            + "ggEAIsXmlWKEgqe6vFJiKJKXzGozG4uUloVqo5fI7XDEOek3XOOmmfUocsiogTloMVIr+jXhVqNY1dsl/mUeCljVWE1hGqpgypGq"
            + "jO6vBWidfNHa1HR7ps7KUt8UjmJI5tqXcGQ9jbjZdC51UWWPWykUapDpiPW7tuVRiskLszn45I5JoCyCh9Wtpg68w/zt/18nmbN4"
            + "L2MWvWYoD6Eyd04p+sI6HtianwTDOlCKJFXCXBmNQDSzgf6Fw/5iDun1+ypyyeP933QGbWa6pCZpfMa77RYC1DAOU+6IkT1046JF"
            + "I4mgdPl7FqwSds6/w6W6YnRkZ+SBAZjTn48x+Z8+PkpZaQKBgQD+B75b7tabRVkYryh7I4iaOmPlRLD6qzEkzqkj44DXvQuQhEoI"
            + "M1cw9Z8JDjUIVeI2UyB67PslejjAKp7asP7uW83vCW3Q4h0DnXRuUaul3dzD3ZWV6joy1L/akBjuKuc9VuryJN4l6JFW+XDJ5/bL"
            + "o/g6YBYXcwTPxMN8nRwEhQKBgQDH4tvgp9woukVxyHHk7ap5Je8AGhEHcj0H1gCxX0GbRy13AHSNR+JIN43t+KGedAiWADwbvigm"
            + "/2AeqFuC89m74Ji9O9OYtu7Ri2hd0pN4I+IuIl+PSGsB+mVNVl0nkLfOcCWUKCEKt1iUzfFlWyeCH0m6HnHYM+YXZ9J5cYQtvQKB"
            + "gQCEtwj1wLbwEe/1XHVat/rDEKRmAFgx7cRUBYAKVu5WR9bF3sdKt3/i/T/kHD8KpsvzM8SxVCP1tX/2rY2mlXfqkZ3qN1PfIHKm"
            + "E9njVNnHYzzWO2rOErvrGIlPJ59Otq1ut30xmp9Q0Ms11EXfxkeCppyl6tM/1wB5h8PV08LyfQKBgQCY5fSRZXWoBcxnHA/bMN2M"
            + "7DJk3ouWKZF7s/w1sYrKz2h8tfFxvMHfwfckSGLAhTuoGifghNXL9t68tMXDZupvp37Oibvy/kOyMDQ2SISbir9R0ONOA/QuwC3L"
            + "ObvqLu4a0Hg+W2/i9ZqhwOLdSBvxkiNgZjdpTVG9J6K9WNLl9QKBgQCWrUVj4HitXPHTDuBs/s8uPdV3bG1y0ySDrwu/DkEvxpy/"
            + "RGsAi8ly9qAPrKiwaJ7qZVBGzV9tk2m2caNKmQ/MpleLhiP8vNgOe1AzCvZ2OKlm6SRoJ+je3PC6a/r2kcranJPoyRQ6TbHbhsLH"
            + "CHvw52BeMdSvtrxBxjRSP1Z22g==";

    private SigningKeys() {}
}
