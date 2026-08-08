package dev.sandoretti.ukun.archunit.compliant;

/**
 * The masked {@code toString()} of {@code docs/11-observabilidad-y-logging.md} §4, written out.
 * Same shape as {@code violations/SignupRequest}, one method apart — which is the whole distance
 * between a leak and a log line.
 */
public record CompliantRequest(String email, String password) {

    @Override
    public String toString() {
        return "CompliantRequest[email=" + email + ", password=***]";
    }
}
