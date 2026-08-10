package dev.sandoretti.ukun.api.identity;

import dev.sandoretti.ukun.api.shared.error.ErrorCode;
import dev.sandoretti.ukun.api.shared.error.UkunException;
import dev.sandoretti.ukun.api.shared.error.ValidationError;
import java.util.List;
import java.util.Map;

/**
 * ukun-auth rejected the credentials on a rule this service does not own — today, the password policy
 * of {@code docs/08-identidad-y-seguridad.md} §8: a minimum length, a maximum imposed by the hashing
 * algorithm, and the check against a known breach corpus.
 *
 * <p>It exists because those rules are deliberately <em>not</em> duplicated here. This service cannot
 * consult a breach corpus and should not hold a second copy of a length bound that would drift from
 * the one that decides; so a password it cannot judge locally is sent, and the verdict comes back over
 * HTTP. Without this exception that verdict arrived as an unhandled client error and the person was
 * told "unexpected error" for typing a short password — a {@code 500} where the contract declares a
 * {@code 400}.
 *
 * <p>The {@code errors[]} are ukun-auth's own, forwarded unchanged, which is what makes the message
 * land under the right input. That is safe to forward precisely because they are already written for a
 * form: a field name and a reason, never the value and never anything about this service's internals.
 */
public final class AccountRejectedException extends UkunException {

    private final List<ValidationError> errors;

    public AccountRejectedException(List<ValidationError> errors) {
        super("The account was rejected by the identity service");
        this.errors = List.copyOf(errors);
    }

    @Override
    public ErrorCode getCode() {
        return ErrorCode.VALIDATION_FAILED;
    }

    @Override
    public Map<String, Object> getExtensions() {
        return Map.of("errors", errors);
    }
}
