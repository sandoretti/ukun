package dev.sandoretti.ukun.api.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The rule of {@code docs/08-identidad-y-seguridad.md} §4, on all four operations: <em>the
 * {@code storeId} in the path identifies the resource and authorises nothing</em>.
 *
 * <p>Every test here forges the path deliberately — Bruno, who works in Barcelona, asking for Alcalá's
 * stock by writing Alcalá's identifier — which is precisely the attempt the legacy takes at face value
 * in three of its four stock handlers. There is nothing subtle to assert: it is a {@code 403}, and the
 * store the request named never gets read.
 *
 * <p>The three impostors are chosen to cover the three ways this can go wrong: an employee of another
 * store (authorisation of a resource), a customer with a perfectly valid token (authorisation by
 * role), and a token whose {@code sub} matches no employee row at all (the case a service that trusted
 * the token's claims instead of the database would let through).
 */
class StockAuthorizationIT extends StockIntegrationTest {

    @Test
    @DisplayName("an employee of another store cannot list this one's stock")
    void deniesListingToAnEmployeeOfAnotherStore() {
        get(alcalaId(), employeeToken(BRUNO)).expectStatus().isForbidden();
    }

    @Test
    @DisplayName("an employee of another store cannot create a line in it")
    void deniesCreatingToAnEmployeeOfAnotherStore() {
        long alcala = alcalaId();

        restTestClient
                .post()
                .uri(stockOf(alcala))
                .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken(BRUNO)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("sku", NEVER_STOCKED, "quantity", 5))
                .exchange()
                .expectStatus()
                .isForbidden();

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM store_stock WHERE store_id = ?", Integer.class, alcala))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("an employee of another store cannot edit or delete this one's lines")
    void deniesWritingToAnEmployeeOfAnotherStore() {
        long alcala = alcalaId();
        long line = lineIdOf(STOCKED_SOFA, alcala);

        restTestClient
                .put()
                .uri(lineOf(alcala, line))
                .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken(BRUNO)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("quantity", 0, "version", 0))
                .exchange()
                .expectStatus()
                .isForbidden();

        restTestClient
                .delete()
                .uri(lineOf(alcala, line))
                .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken(BRUNO)))
                .exchange()
                .expectStatus()
                .isForbidden();

        assertThat(jdbc.queryForObject("SELECT quantity FROM store_stock WHERE id = ?", Integer.class, line))
                .isEqualTo(7);
    }

    /**
     * A token that carries the right shape and no employee behind it. It is the case that separates
     * "the claims say so" from "the database says so": nothing in this token is invalid, and the answer
     * is still a {@code 403}.
     */
    @Test
    @DisplayName("a token with no employee row behind it is denied")
    void deniesATokenWithNoEmployeeBehindIt() {
        get(alcalaId(), employeeToken(GHOST)).expectStatus().isForbidden();
    }

    @Test
    @DisplayName("a customer cannot reach the stock of any store")
    void deniesACustomer() {
        get(alcalaId(), customerToken(UUID.randomUUID())).expectStatus().isForbidden();
    }

    @Test
    @DisplayName("without a token it is a 401, and the body says only that")
    void deniesAnAnonymousCaller() {
        restTestClient
                .get()
                .uri(stockOf(alcalaId()))
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("UNAUTHENTICATED");
    }

    /** The denial carries no detail about the store: same body whoever asked and whatever they asked for. */
    @Test
    @DisplayName("the forbidden body is the contract's, with a code and a traceId and nothing else")
    void answersForbiddenWithTheContractBody() {
        get(alcalaId(), employeeToken(BRUNO))
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("FORBIDDEN")
                .jsonPath("$.traceId")
                .isNotEmpty()
                .jsonPath("$.resource")
                .doesNotExist();
    }

    private RestTestClient.ResponseSpec get(long storeId, String token) {
        return restTestClient
                .get()
                .uri(stockOf(storeId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange();
    }
}
