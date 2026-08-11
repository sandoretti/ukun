package dev.sandoretti.ukun.api.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

/**
 * Defect #2 written as a test: two customers buying the last unit at the same time.
 *
 * <p>The legacy {@code PedidoServiceImpl.reducirStockOnline} reads the stock, subtracts in Java and
 * writes it back, with nothing holding the row in between. Two concurrent orders both read "1
 * available", both write "0", and the shop has sold two of something it had one of. ADR-0008 answers
 * with a pessimistic lock ordered by stock line id, and this is where that claim is checked against a
 * real database rather than against a diagram.
 *
 * <p><strong>Remove the {@code @Lock(PESSIMISTIC_WRITE)} from
 * {@code StoreStockRepository.lockLinesOf} and this suite goes red</strong> — verified by doing it,
 * not assumed. What goes red is {@link #tellsTheLoserWhatHappened()}, and the way it fails is worth
 * knowing: the shop still does not oversell, because {@code StoreStock} carries a {@code @Version} and
 * the second transaction dies on it, but the customer who lost the race is answered
 * {@code 500 INTERNAL_ERROR} instead of {@code 409 INSUFFICIENT_STOCK}. The three defences of ADR-0008
 * are layered, and this suite is what tells them apart: consistency survives without the lock, a
 * comprehensible answer does not.
 *
 * <p>{@link #sellsTheLastUnitExactlyOnce()} is the invariant and is the one to keep even though it can
 * pass without the lock — whether the two requests genuinely overlap is a matter of scheduling, and an
 * assertion about stock never going negative should not be deleted because it is sometimes easy. The
 * exhaustive, N-threaded version of all this is issue #37.
 *
 * <p>Two threads and not twenty, deliberately. What is under test is the invariant, and two contending
 * transactions are enough to break a read-modify-write; a thread count tuned until it fails is how a
 * flaky suite is born.
 */
class CheckoutConcurrencyIT extends OrdersIntegrationTest {

    private static final int TIMEOUT_SECONDS = 30;

    /**
     * The whole point. One unit, two customers, two attempts opened before either confirms — so both
     * were told the unit was there, which is exactly the collision ADR-0016 says the attempt does not
     * prevent and the lock does.
     */
    @Test
    @DisplayName("two customers confirming the last unit: exactly one 201, one 409, and no negative stock")
    void sellsTheLastUnitExactlyOnce() {
        addToCart(ADA, productIdOf(LAST_ONE), 1);
        addToCart(GRACE, productIdOf(LAST_ONE), 1);
        UUID adaAttempt = openAttempt(ADA).attemptId();
        UUID graceAttempt = openAttempt(GRACE).attemptId();

        List<Integer> statuses = inParallel(
                () -> confirmFor(ADA, adaAttempt), () -> confirmFor(GRACE, graceAttempt));

        assertSoftly(softly -> {
            softly.assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            softly.assertThat(onlineStockOf(LAST_ONE)).isZero();
            softly.assertThat(placedOrders()).isEqualTo(1);
        });
    }

    /** The loser is told why, and with the extensions the frontend interpolates into the message. */
    @Test
    @DisplayName("the customer who loses the race is told the stock ran out")
    void tellsTheLoserWhatHappened() {
        addToCart(ADA, productIdOf(LAST_ONE), 1);
        addToCart(GRACE, productIdOf(LAST_ONE), 1);
        UUID adaAttempt = openAttempt(ADA).attemptId();
        UUID graceAttempt = openAttempt(GRACE).attemptId();

        List<String> codes = inParallel(
                () -> codeFor(ADA, adaAttempt), () -> codeFor(GRACE, graceAttempt));

        assertThat(codes).containsExactlyInAnyOrder("", "INSUFFICIENT_STOCK");
    }

    /**
     * The other race, and the one a customer causes by pressing the button twice: same key, two
     * requests, no time in between.
     *
     * <p>The second answer is {@code 409 IDEMPOTENCY_IN_PROGRESS} if it arrives while the first is
     * still inside its transaction, and {@code 200} with the stored order if it arrives after — both
     * are correct, and which one happens is a matter of microseconds. What is <strong>not</strong>
     * negotiable is that there is one order and the stock moved once, which is what this asserts.
     */
    @Test
    @DisplayName("a double submit of the same attempt creates exactly one order")
    void createsOneOrderForADoubleSubmit() {
        addToCart(ADA, productIdOf(IN_STOCK), 2);
        UUID attemptId = openAttempt(ADA).attemptId();

        List<Integer> statuses = inParallel(() -> confirmFor(ADA, attemptId), () -> confirmFor(ADA, attemptId));

        assertSoftly(softly -> {
            softly.assertThat(statuses).contains(201);
            softly.assertThat(statuses).anyMatch(status -> status == 200 || status == 409);
            softly.assertThat(placedOrders()).isEqualTo(1);
            softly.assertThat(onlineStockOf(IN_STOCK)).isEqualTo(5);
        });
    }

    /**
     * Both calls start from the same latch, so they are in flight together rather than one after the
     * other. Virtual threads because the two requests spend their time waiting — one of them on a row
     * lock held by the other, which is the situation being reproduced.
     */
    private static <T> List<T> inParallel(Callable<T> first, Callable<T> second) {
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<T>> running = List.of(submit(executor, start, first), submit(executor, start, second));
            start.countDown();

            return running.stream().map(CheckoutConcurrencyIT::resultOf).toList();
        }
    }

    private static <T> Future<T> submit(ExecutorService executor, CountDownLatch start, Callable<T> call) {
        return executor.submit(() -> {
            start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return call.call();
        });
    }

    private static <T> T resultOf(Future<T> running) {
        try {
            return running.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for a checkout", interrupted);
        } catch (Exception failure) {
            throw new IllegalStateException("A concurrent checkout did not finish", failure);
        }
    }

    private int confirmFor(UUID accountId, UUID attemptId) {
        return post(accountId, attemptId).getStatus().value();
    }

    /** The {@code code} of a rejected checkout, and the empty string for the one that succeeded. */
    private String codeFor(UUID accountId, UUID attemptId) {
        EntityExchangeResult<String> result = post(accountId, attemptId);

        return result.getStatus().is2xxSuccessful()
                ? ""
                : String.valueOf(result.getResponseBody()).replaceAll(".*\"code\"\\s*:\\s*\"([A-Z_]+)\".*", "$1");
    }

    private EntityExchangeResult<String> post(UUID accountId, UUID attemptId) {
        return restTestClient
                .post()
                .uri(ORDERS)
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(accountId)))
                .header(IDEMPOTENCY_KEY, attemptId.toString())
                .exchange()
                .expectBody(String.class)
                .returnResult();
    }

    private int placedOrders() {
        return jdbc.queryForObject("SELECT count(*) FROM customer_order", Integer.class);
    }
}
