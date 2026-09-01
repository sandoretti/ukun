package dev.sandoretti.ukun.api.shared.scheduling;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Makes a {@code @Scheduled} method run once per firing across every replica, not once per replica
 * (ADR-0024, issue #118).
 *
 * <p>A near-copy of the class of the same name in {@code ukun-auth}, and deliberately not shared:
 * ADR-0001 rules out a module of common code, and what the two have in common is four lines of
 * builder — the interesting part is the rule about <em>which</em> jobs take the lock, and that lives
 * in the skills, where it gets read.
 *
 * <p>This service had no {@code @Scheduled} at all until v1.0. The first one is
 * {@code CheckoutPurgeJob}, whose {@code DELETE … WHERE} would be waste and not a defect if it ran
 * twice — it holds the lock because it is the template the next job gets copied from, and the next
 * one may be the one that sends something.
 *
 * <p>In {@code shared} because it configures the whole application and owns no table: an
 * {@code ApplicationModule} of type {@code OPEN} whose internals every module may see
 * ({@code shared/package-info.java}). No module imports anything from this package — the wiring is
 * the annotation on the job, not a type reference.
 */
@Configuration(proxyBeanMethods = false)
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulerLockConfig {

    /**
     * Postgres's clock decides whether a lock expired, not each replica's — see the sibling class.
     *
     * <p>The {@link JdbcTemplate} is injected rather than built: Boot already contributes one beside
     * the datasource, and a second instance over the same one is a bean nobody would go looking for.
     */
    @Bean
    LockProvider lockProvider(JdbcTemplate jdbcTemplate) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(jdbcTemplate)
                .usingDbTime()
                .build());
    }
}
