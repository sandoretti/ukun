package dev.sandoretti.ukun.auth.error;

import java.util.Map;
import java.util.UUID;

/**
 * No account with that identifier — the {@code 404} of {@code :activate} in
 * {@code docs/api/openapi/ukun-auth.yaml}.
 *
 * <p>Note which of the two operations throws it and which does not. Activating a non-existent account
 * is a real failure: the saga is trying to commit something that was never reserved. Releasing one is
 * not, and {@code :release} answers {@code 204} either way — it is a best-effort compensation whose
 * whole contract is "the account is gone", and reporting a failure when the desired end state already
 * holds would give the caller something to retry for no reason.
 */
public final class AccountNotFoundException extends UkunException {

    private final UUID accountId;

    public AccountNotFoundException(UUID accountId) {
        super("account %s does not exist".formatted(accountId));
        this.accountId = accountId;
    }

    @Override
    public ErrorCode getCode() {
        return ErrorCode.RESOURCE_NOT_FOUND;
    }

    @Override
    public Map<String, Object> getExtensions() {
        return Map.of("resource", "account", "id", accountId.toString());
    }
}
