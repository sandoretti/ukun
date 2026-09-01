package dev.sandoretti.ukun.auth.config;

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
 * <h2>Which jobs take the lock, and which must not</h2>
 *
 * The rule lives in the {@code spring-auth} skill; the short version has three cases and only the
 * first is obvious.
 *
 * <ul>
 *   <li><strong>Required</strong> when the effect is not repeatable: it sends something, moves money,
 *       calls a third party, or writes derived state. There is no such job in this system yet, which
 *       is why none of them <em>needs</em> the lock today.
 *   <li><strong>Idempotence is enough</strong> for a {@code DELETE … WHERE}. Running it on N replicas
 *       is waste, not a defect — the issue that raised this says so in those words.
 *   <li><strong>Forbidden</strong> when the state belongs to one JVM. The gateway's
 *       {@code RequestRateLimitBuckets.discardIdleBuckets} sweeps an in-memory map: locking it would
 *       leave every replica but one growing that map for ever, turning the rate limiter into the
 *       denial of service it exists to prevent. That is the half of the rule people forget.
 * </ul>
 *
 * <p>{@code AccountReconciliationJob} takes the lock even though the second case would excuse it,
 * because it is the template the next job in this service gets copied from — and the next one may be
 * the one that sends something. {@code LoginAttemptBuckets.discardIdleBuckets} does not: it runs every
 * ten minutes, and a lock row rewritten that often costs more contention than the delete it guards.
 *
 * <h2>{@code usingDbTime}</h2>
 *
 * Whether a lock has expired is decided by Postgres's clock and not by each JVM's. With N replicas,
 * the clock nobody can trust is precisely the local one: a machine running a minute fast would take a
 * lock another replica still holds, and the failure would look like the lock not working at all.
 */
@Configuration(proxyBeanMethods = false)
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
class SchedulerLockConfig {

    /**
     * El {@link JdbcTemplate} se inyecta y no se construye: {@code AuthorizationServerConfig} ya
     * declara ese bean para las dos tablas del Authorization Server, y una segunda instancia sobre el
     * mismo {@code DataSource} no aporta nada que no tenga ya nombre.
     */
    @Bean
    LockProvider lockProvider(JdbcTemplate jdbcTemplate) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(jdbcTemplate)
                .usingDbTime()
                .build());
    }
}
