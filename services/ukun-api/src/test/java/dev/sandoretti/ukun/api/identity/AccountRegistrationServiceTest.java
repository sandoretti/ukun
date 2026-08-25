package dev.sandoretti.ukun.api.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The order of the saga and what it does when a step fails — the part of
 * {@code docs/08-identidad-y-seguridad.md} §3 that is the same for a customer and for an employee, and
 * the reason this class was lifted out of {@code SignupService} when the back-office needed it too.
 *
 * <p>It is a unit test and it can be, because the saga's argument is about ordering and compensation
 * rather than about persistence: the local transaction is a callback here, which is exactly how the
 * real one is handed in. The two suites that exercise it against a database, {@code SignupIT} and
 * {@code EmployeeCreationIT}, assert what their own module writes; what neither of them can provoke
 * without this level is a local write that fails <em>after</em> the reservation.
 */
class AccountRegistrationServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("6c3a2f10-0000-4000-8000-000000000001");

    private static final long LOCAL_ID = 42L;

    private static final CreateAccountCommand COMMAND =
            new CreateAccountCommand("nueva@ukun.com", "una-contrasena-larga", Set.of(Role.EMPLOYEE), false);

    private final AccountsPort accounts = mock(AccountsPort.class);

    private final AccountRegistrationService registrations = new AccountRegistrationService(accounts);

    @Test
    @DisplayName("the happy path reserves, writes and activates, and reports what was written")
    void reservesWritesAndActivates() {
        given(accounts.reserve(any(), any())).willReturn(new AccountRef(ACCOUNT_ID, true));

        Registration registration = registrations.register(COMMAND, accountId -> LOCAL_ID);

        assertThat(registration.outcome()).isEqualTo(RegistrationOutcome.COMPLETED);
        assertThat(registration.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(registration.localId()).isEqualTo(LOCAL_ID);
        assertThat(registration.registrationId()).isNotNull();
        then(accounts).should().activate(ACCOUNT_ID);
    }

    /** The local write is handed the account that was just reserved, and nothing else. */
    @Test
    @DisplayName("the local write runs after the reservation, on the reserved account")
    void writesLocallyOnTheReservedAccount() {
        given(accounts.reserve(any(), any())).willReturn(new AccountRef(ACCOUNT_ID, true));

        registrations.register(COMMAND, accountId -> {
            assertThat(accountId).isEqualTo(ACCOUNT_ID);
            return LOCAL_ID;
        });
    }

    /**
     * A taken email is decided before anything local happens, which is the whole reason the reservation
     * comes first: there is no orphan profile to clean up because there was never one to write.
     */
    @Test
    @DisplayName("a taken email is propagated and the local write never runs")
    void propagatesATakenEmailWithoutWritingAnything() {
        willThrow(new EmailAlreadyExistsException()).given(accounts).reserve(any(), any());

        assertThatThrownBy(() -> registrations.register(COMMAND, accountId -> {
                    throw new AssertionError("the local write must not run after a failed reservation");
                }))
                .isInstanceOf(EmailAlreadyExistsException.class);

        then(accounts).should(never()).release(any());
        then(accounts).should(never()).activate(any());
    }

    @Test
    @DisplayName("a local write that fails releases the reservation and propagates its own failure")
    void compensatesAFailedLocalWrite() {
        given(accounts.reserve(any(), any())).willReturn(new AccountRef(ACCOUNT_ID, true));

        assertThatThrownBy(() -> registrations.register(COMMAND, accountId -> {
                    throw new IllegalStateException("the store vanished");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("the store vanished");

        then(accounts).should().release(ACCOUNT_ID);
        then(accounts).should(never()).activate(any());
    }

    /**
     * The compensation is best-effort, so its own failure must not replace the exception that caused it:
     * the caller's problem is the local write, and reconciliation collects the account either way.
     */
    @Test
    @DisplayName("a release that also fails does not mask the failure that caused it")
    void keepsTheOriginalFailureWhenTheReleaseAlsoFails() {
        given(accounts.reserve(any(), any())).willReturn(new AccountRef(ACCOUNT_ID, true));
        willThrow(new IllegalStateException("ukun-auth is down")).given(accounts).release(ACCOUNT_ID);

        assertThatThrownBy(() -> registrations.register(COMMAND, accountId -> {
                    throw new IllegalStateException("the store vanished");
                }))
                .hasMessage("the store vanished");
    }

    /**
     * Three attempts — the initial one plus two retries — and then the {@code 202}. The profile stays:
     * the person exists, their account does not work yet, and repeating the registration finishes it.
     */
    @Test
    @DisplayName("an activation that never lands is reported as pending, not as a failure")
    void reportsAnExhaustedActivationAsPending() {
        given(accounts.reserve(any(), any())).willReturn(new AccountRef(ACCOUNT_ID, true));
        willThrow(new IllegalStateException("ukun-auth is down")).given(accounts).activate(ACCOUNT_ID);

        Registration registration = registrations.register(COMMAND, accountId -> LOCAL_ID);

        assertThat(registration.outcome()).isEqualTo(RegistrationOutcome.ACTIVATION_PENDING);
        assertThat(registration.localId()).isEqualTo(LOCAL_ID);
        then(accounts).should(times(3)).activate(ACCOUNT_ID);
        then(accounts).should(never()).release(any());
    }
}
