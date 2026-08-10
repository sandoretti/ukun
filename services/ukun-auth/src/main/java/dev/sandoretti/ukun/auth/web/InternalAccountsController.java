package dev.sandoretti.ukun.auth.web;

import dev.sandoretti.ukun.auth.application.AccountsService;
import dev.sandoretti.ukun.auth.application.ReservedAccount;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The three operations of {@code docs/api/openapi/ukun-auth.yaml}, and the whole HTTP surface this
 * service offers besides the Authorization Server's own standard endpoints.
 *
 * <p><strong>Never the browser.</strong> These are service-to-service: ukun-api calls them with
 * {@code client_credentials} and the {@code accounts:write} scope, inside the container network. They
 * are not routed from ukun-gateway and there is no path from the SPA to here — the scope is enforced
 * by the filter chain of {@code InternalApiSecurityConfig}, not by this class.
 *
 * <p>It holds no logic. Deciding whether a reservation is new, whether an activation is a replay, or
 * what a constraint violation meant is {@link AccountsService}'s, because those decisions have to hold
 * for every caller and not only for one that arrived over HTTP. What is left here is the translation
 * between that and the contract's status codes, which is exactly what a controller should be.
 */
@RestController
@RequestMapping(path = "/internal/accounts", produces = MediaType.APPLICATION_JSON_VALUE)
class InternalAccountsController {

    private final AccountsService accounts;

    InternalAccountsController(AccountsService accounts) {
        this.accounts = accounts;
    }

    /**
     * Step 2 of the saga: reserve the email with a disabled account.
     *
     * <p>The two success codes are the contract's, and the difference between them is the only thing
     * the caller cannot work out for itself: {@code 201} when this call created the account,
     * {@code 200} when the same {@code Idempotency-Key} had already reserved one and the body is the
     * <em>same</em> {@code accountId}. Both are successes; a retry is not an error.
     *
     * <p>The header is required by the contract, so a request without it is a {@code 400} decided by
     * Spring's own binding rather than by a check here.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<AccountRefResponse> createAccount(
            @RequestHeader("Idempotency-Key") UUID idempotencyKey, @Valid @RequestBody CreateAccountRequest request) {

        ReservedAccount reserved =
                accounts.reserve(idempotencyKey, request.email(), request.password(), request.roles());

        return ResponseEntity.status(reserved.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(new AccountRefResponse(reserved.accountId()));
    }

    /**
     * Step 4 of the saga: the real commit point of a signup.
     *
     * <p>The {@code :activate} suffix is the contract's spelling, and it is a deliberate choice of
     * that document over inventing a sub-resource: activation is an operation on the account, not a
     * thing the account has.
     */
    @PostMapping("/{accountId}:activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void activateAccount(@PathVariable UUID accountId) {
        accounts.activate(accountId);
    }

    /**
     * Compensation for step 3, invoked best-effort when ukun-api's local transaction fails after the
     * reservation.
     *
     * <p>{@code 204} whether the account was there or not, as the contract says ("released, or already
     * gone"). The caller wants the email free; if it already is, there is nothing to report and
     * nothing to retry.
     */
    @PostMapping("/{accountId}:release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void releaseAccount(@PathVariable UUID accountId) {
        accounts.release(accountId);
    }
}
