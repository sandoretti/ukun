package dev.sandoretti.ukun.api.identity;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Two endpoints that exist only for the tests of this package: one that any authenticated caller may
 * reach, one that only an admin may.
 *
 * <p>They are here because the thing under test — "a protected endpoint answers 401 without a token,
 * 403 with the wrong role and 200 with the right one" — needs a protected endpoint, and this service
 * has none yet: everything implemented so far is public by contract, and the first real protected
 * route arrives with {@code /api/me/profile}. Adding one to production code to make a test possible
 * would put a route in the service that the frozen OpenAPI does not declare.
 *
 * <p>The controller is nested inside a {@code @TestConfiguration} on purpose, and that is not
 * decoration: a plain {@code @RestController} sitting in {@code src/test} under this package would be
 * picked up by the component scan of <em>every</em> {@code @SpringBootTest} in the suite, including
 * the ones that diff the generated OpenAPI document against the frozen contract — which would then be
 * comparing a document with two routes nobody wrote. Spring's test type filter excludes a class whose
 * enclosing class is a {@code @TestConfiguration}, so this one only exists where it is asked for by
 * name: {@code @WebMvcTest(controllers = ...)} here, an explicit {@code @Import} in the IT.
 */
@TestConfiguration
public class ProbeSecuredEndpoints {

    public static final String AUTHENTICATED = "/probe/authenticated";
    public static final String ADMIN_ONLY = "/probe/admin";

    @RestController
    @RequestMapping("/probe")
    public static class ProbeController {

        /** Protected by the filter chain's {@code anyRequest().authenticated()} and nothing else. */
        @GetMapping("/authenticated")
        String authenticated() {
            return "authenticated";
        }

        /**
         * Protected by method security, which is the path that publishes {@code AuthorizationDeniedEvent}
         * — so this endpoint also exercises the audit listener, not only the status code.
         */
        @PreAuthorize("hasRole('ADMIN')")
        @GetMapping("/admin")
        String adminOnly() {
            return "admin";
        }
    }
}
