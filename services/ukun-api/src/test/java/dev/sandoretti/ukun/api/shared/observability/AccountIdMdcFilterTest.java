package dev.sandoretti.ukun.api.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * The filter that names the person in the log ({@code docs/11-observabilidad-y-logging.md} §5).
 *
 * <p>Three of the four tests are about the {@code finally}, and that ratio is on purpose. Putting a
 * value into the MDC is the easy half and it fails loudly; failing to take it out is silent, survives
 * every test that checks the happy path, and only shows up in production as one user's request logged
 * under another user's account. That is a correlation bug that reads exactly like a security bug, and
 * ruling it out costs hours — so it is asserted directly, including on the path where the request
 * blew up.
 */
class AccountIdMdcFilterTest {

    private static final String ACCOUNT_ID = "accountId";

    private static final String SUBJECT = UUID.randomUUID().toString();

    private final AccountIdMdcFilter filter = new AccountIdMdcFilter();

    private final MockHttpServletRequest request = new MockHttpServletRequest();

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearEverythingThreadBound() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    @DisplayName("an authenticated request is served with the token's subject in the MDC")
    void namesTheRequestWithTheSubjectOfTheToken() throws Exception {
        authenticateAs(SUBJECT);
        List<String> seenByTheChain = new ArrayList<>();

        filter.doFilter(request, response, (req, res) -> seenByTheChain.add(MDC.get(ACCOUNT_ID)));

        assertThat(seenByTheChain).containsExactly(SUBJECT);
    }

    @Test
    @DisplayName("the key is gone by the time the request is over")
    void leavesNothingBehindOnThePooledThread() throws Exception {
        authenticateAs(SUBJECT);

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(MDC.get(ACCOUNT_ID)).isNull();
    }

    /**
     * The case a {@code try/finally} exists for and a plain {@code remove()} after the call would
     * miss. Tomcat hands the thread straight to the next request, so an exception escaping the chain
     * is precisely when the key would survive into somebody else's.
     */
    @Test
    @DisplayName("it is also gone when the request failed")
    void leavesNothingBehindWhenTheChainThrows() {
        authenticateAs(SUBJECT);

        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
                    throw new ServletException("the handler blew up");
                }))
                .isInstanceOf(ServletException.class);

        assertThat(MDC.get(ACCOUNT_ID)).isNull();
    }

    /**
     * Most of the catalogue is served to nobody in particular. An anonymous request is not a failure
     * to name someone — there is no one — so the key is never written, and a stale value from an
     * earlier request on this thread would not be overwritten into looking current either.
     */
    @Test
    @DisplayName("an anonymous request writes no account into the MDC")
    void writesNothingForAnAnonymousRequest() throws Exception {
        List<String> seenByTheChain = new ArrayList<>();

        filter.doFilter(request, response, (req, res) -> seenByTheChain.add(MDC.get(ACCOUNT_ID)));

        assertThat(seenByTheChain).containsOnlyNulls();
    }

    private static void authenticateAs(String subject) {
        Jwt token = Jwt.withTokenValue("a-token")
                .header("alg", "RS256")
                .subject(subject)
                .claim("roles", List.of("CUSTOMER"))
                .build();

        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token));
    }
}
