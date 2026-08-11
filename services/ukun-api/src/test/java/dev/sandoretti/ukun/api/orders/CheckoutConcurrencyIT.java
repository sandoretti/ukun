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
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Defect #2 written as a test: a crowd of customers buying the last unit at the same time.
 *
 * <p>The legacy {@code PedidoServiceImpl.reducirStockOnline} reads the stock, subtracts in Java and
 * writes it back, with nothing holding the row in between. Two concurrent orders both read "1
 * available", both write "0", and the shop has sold two of something it had one of. ADR-0008 answers
 * with a pessimistic lock ordered by stock line id, and this is where that claim is checked against a
 * real database rather than against a diagram.
 *
 * <p><strong>Remove the {@code @Lock(PESSIMISTIC_WRITE)} from
 * {@code StoreStockRepository.lockLinesOf} and four of these five tests go red</strong> — verified by
 * doing it, not assumed. Only {@link #createsOneOrderForADoubleSubmit()} survives, which is right: it
 * is about the idempotency key and never contends for a row.
 *
 * <p>The way they fail is the point. The shop does <strong>not</strong> oversell — {@code StoreStock}
 * carries a {@code @Version} and the losing transactions die on it — so nothing is lost that money
 * cares about. What breaks is the answer: every customer who lost the race is handed
 * {@code 500 INTERNAL_ERROR} instead of {@code 409 INSUFFICIENT_STOCK}, with no {@code sku} and no
 * figures to interpolate. And it goes further than one lost sale: in {@link #sellsEveryUnitAndNoMore()}
 * the shop serves <em>one</em> order where three could have been served, because the optimistic
 * failure kills transactions that had stock waiting for them. Without the lock the shop undersells and
 * apologises in a language the frontend cannot read.
 *
 * <p>That is the three defences of ADR-0008 pulled apart: consistency survives without the lock, a
 * comprehensible answer does not, and {@code ck_store_stock_quantity} never has to be reached at all.
 *
 * <p>{@link #sellsTheLastUnitExactlyOnce()} is the invariant and is the one to keep even though it can
 * pass without the lock — whether the requests genuinely overlap is a matter of scheduling, and an
 * assertion about stock never going negative should not be deleted because it is sometimes easy.
 *
 * <h2>Why there is no deadlock test here</h2>
 *
 * <p>The {@code order by stock.id} of that same query exists to stop two orders sharing products in
 * different sequences from each holding the row the other waits for. That scenario <strong>cannot be
 * built through this API</strong>, and the reason is a second normalisation nobody has to remember:
 * {@code CheckoutAttempt.lines} carries {@code @OrderBy("id.productId ASC")}, so every confirmation
 * hands {@code inventory} its quantities in the same sequence whatever order the carts were filled in.
 * A test claiming to prove the lock ordering would pass with the {@code order by} removed — and a test
 * that passes either way proves nothing ({@code docs/13-testing.md}, "sin tests basura"). What is
 * tested instead is the fact a broken ordering would actually produce for a customer:
 * {@link #neverDecrementsHalfAMultiLineOrder()} fails on a {@code 500} and on a partial decrement.
 *
 * <p>{@link #BUYERS} buyers and not twenty. What is under test is an invariant, and a thread count
 * tuned until it fails is how a flaky suite is born.
 */
class CheckoutConcurrencyIT extends OrdersIntegrationTest {

    private static final int TIMEOUT_SECONDS = 30;

    /**
     * Two apiece out of the sofa's seven, so that exactly three buyers can be served in full and the
     * count is arithmetic rather than a race: seven divided by two, with one unit that nobody can use.
     */
    private static final int UNITS_EACH = 2;

    private static final int SERVABLE_BUYERS = 3;

    private final ObjectMapper json = new ObjectMapper();

    /**
     * The whole point. One unit, {@link #BUYERS} customers, every attempt opened before any of them
     * confirms — so all of them were told the unit was there, which is exactly the collision ADR-0016
     * says the attempt does not prevent and the lock does.
     */
    @Test
    @DisplayName("many customers confirming the last unit: exactly one 201, the rest 409, no negative stock")
    void sellsTheLastUnitExactlyOnce() {
        List<Answer> answers = raceFor(LAST_ONE, 1);

        assertSoftly(softly -> {
            softly.assertThat(statusesOf(answers)).hasSize(BUYERS).containsOnly(201, 409);
            softly.assertThat(winnersAmong(answers)).isEqualTo(1);
            softly.assertThat(onlineStockOf(LAST_ONE)).isZero();
            softly.assertThat(placedOrders()).isEqualTo(1);
        });
    }

    /**
     * The same race where there is more than one unit to go round: the shop sells every unit it can
     * serve in full and refuses the rest. Seven units and two apiece is deliberate arithmetic — three
     * buyers are served, five are turned away, and one unit is left over because RF-04 has no partial
     * orders and nobody gets half a sofa.
     */
    @Test
    @DisplayName("more buyers than stock: every servable order is served, and not one more")
    void sellsEveryUnitAndNoMore() {
        List<Answer> answers = raceFor(IN_STOCK, UNITS_EACH);

        assertSoftly(softly -> {
            softly.assertThat(statusesOf(answers)).hasSize(BUYERS).containsOnly(201, 409);
            softly.assertThat(winnersAmong(answers)).isEqualTo(SERVABLE_BUYERS);
            softly.assertThat(onlineStockOf(IN_STOCK)).isEqualTo(1);
            softly.assertThat(placedOrders()).isEqualTo(SERVABLE_BUYERS);
        });
    }

    /**
     * Every loser is told why, and with the extensions the frontend interpolates into the message
     * ({@code docs/12-error-contract.md}).
     *
     * <p>{@code PlaceOrderIT} already asserts the shape of this problem for a customer who was simply
     * too late; what is new here is where the numbers come from. {@code available} is {@code 0} and not
     * {@code 1} because the figure is read <em>after</em> the winner committed, through the lock — the
     * attempt these customers hold still says the unit is there, and the answer they get does not.
     */
    @Test
    @DisplayName("every customer who loses the race is told the stock ran out, and by how much")
    void tellsEveryLoserWhatHappened() {
        List<Answer> answers = raceFor(LAST_ONE, 1);

        assertThat(losersAmong(answers)).hasSize(BUYERS - 1).allSatisfy(problem -> assertSoftly(softly -> {
            softly.assertThat(problem.path("code").asString()).isEqualTo("INSUFFICIENT_STOCK");
            softly.assertThat(problem.path("sku").asString()).isEqualTo(LAST_ONE);
            softly.assertThat(problem.path("available").asInt()).isZero();
            softly.assertThat(problem.path("requested").asInt()).isEqualTo(1);
            softly.assertThat(problem.has("stockLineId")).isTrue();
        }));
    }

    /**
     * Two lines, one of them scarce, and a crowd confirming at once — the shape that would expose a
     * decrement applied line by line as the transactions interleave.
     *
     * <p>The sofa is checked before the litera (the attempt orders its lines by product id, and the
     * sofa was created first), so a service that decremented as it went would take a sofa from every
     * buyer and only then discover that the litera is gone. The assertion that catches it is the plain
     * one: six sofas left, not fewer. It is also where a broken lock ordering would surface, as a
     * {@code 500} from a deadlock rather than an answer.
     */
    @Test
    @DisplayName("a rejected multi-line order decrements nothing, however many are confirming at once")
    void neverDecrementsHalfAMultiLineOrder() {
        List<UUID> buyers = seedBuyers(BUYERS);
        buyers.forEach(buyer -> {
            addToCart(buyer, productIdOf(IN_STOCK), 1);
            addToCart(buyer, productIdOf(LAST_ONE), 1);
        });

        List<Answer> answers = confirmTogether(buyers);

        assertSoftly(softly -> {
            softly.assertThat(statusesOf(answers)).hasSize(BUYERS).containsOnly(201, 409);
            softly.assertThat(winnersAmong(answers)).isEqualTo(1);
            softly.assertThat(onlineStockOf(IN_STOCK)).isEqualTo(6);
            softly.assertThat(onlineStockOf(LAST_ONE)).isZero();
            softly.assertThat(placedOrders()).isEqualTo(1);
        });
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

        List<Answer> answers = inParallel(List.of(() -> post(ADA, attemptId), () -> post(ADA, attemptId)));

        assertSoftly(softly -> {
            softly.assertThat(statusesOf(answers)).contains(201);
            softly.assertThat(statusesOf(answers)).anyMatch(status -> status == 200 || status == 409);
            softly.assertThat(placedOrders()).isEqualTo(1);
            softly.assertThat(onlineStockOf(IN_STOCK)).isEqualTo(5);
        });
    }

    /**
     * A crowd of buyers, a cart apiece and an attempt apiece, all opened before anybody confirms: every
     * one of them has been told the units are there.
     */
    private List<Answer> raceFor(String sku, int quantity) {
        List<UUID> buyers = seedBuyers(BUYERS);
        buyers.forEach(buyer -> addToCart(buyer, productIdOf(sku), quantity));

        return confirmTogether(buyers);
    }

    /** Opens every attempt first, then confirms them all at once. The order matters; see {@link #raceFor}. */
    private List<Answer> confirmTogether(List<UUID> buyers) {
        List<UUID> attempts = buyers.stream().map(buyer -> openAttempt(buyer).attemptId()).toList();

        return inParallel(IntStream.range(0, buyers.size())
                .<Callable<Answer>>mapToObj(index -> () -> post(buyers.get(index), attempts.get(index)))
                .toList());
    }

    /**
     * Every call starts from the same latch, so they are in flight together rather than one after the
     * other. Virtual threads because the requests spend their time waiting — all but one of them on a
     * row lock held by another, which is the situation being reproduced.
     *
     * <p>No {@code Thread.sleep} and no polling: the latch releases them and {@link Future#get} waits
     * for the answers ({@code docs/13-testing.md}).
     */
    private static <T> List<T> inParallel(List<Callable<T>> calls) {
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<T>> running =
                    calls.stream().map(call -> submit(executor, start, call)).toList();
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

    private Answer post(UUID accountId, UUID attemptId) {
        EntityExchangeResult<String> result = restTestClient
                .post()
                .uri(ORDERS)
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(accountId)))
                .header(IDEMPOTENCY_KEY, attemptId.toString())
                .exchange()
                .expectBody(String.class)
                .returnResult();

        return new Answer(result.getStatus().value(), json.readTree(String.valueOf(result.getResponseBody())));
    }

    private static List<Integer> statusesOf(List<Answer> answers) {
        return answers.stream().map(Answer::status).toList();
    }

    private static int winnersAmong(List<Answer> answers) {
        return (int) answers.stream().filter(answer -> answer.status() == 201).count();
    }

    /** The bodies of the rejected checkouts, which are {@code ProblemDetail}s. */
    private static List<JsonNode> losersAmong(List<Answer> answers) {
        return answers.stream()
                .filter(answer -> answer.status() != 201)
                .map(Answer::body)
                .toList();
    }

    private int placedOrders() {
        return jdbc.queryForObject("SELECT count(*) FROM customer_order", Integer.class);
    }

    /** One checkout's answer: the status, and the body parsed once so several tests can read it. */
    private record Answer(int status, JsonNode body) {}
}
