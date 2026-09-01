package dev.sandoretti.ukun.auth.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.jdbc.PrimaryKeyMapper;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.postgresql.Bucket4jPostgreSQL;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * One token bucket per {@code IP + email}, {@code docs/08-identidad-y-seguridad.md} §8.
 *
 * <p><strong>Why the key is the pair and not just the account.</strong> Counting failures per account
 * turns the protection into the attack: anybody who knows an email can lock its owner out by failing
 * to log in as them. Counting per IP alone punishes everyone behind one NAT. The pair throttles the
 * attempt that is actually repeating.
 *
 * <p><strong>The backoff is progressive because there are two bandwidths, not one.</strong> A person
 * who mistypes their password gets five quick tries and is never aware of this class; a script that
 * keeps going hits the hourly limit and gets a window measured in minutes rather than seconds. One
 * limit alone can only be lenient or hostile, and would have to pick.
 *
 * <h2>In {@code ukun_auth} since v1.0, not in this JVM</h2>
 *
 * Until then these buckets were a {@code ConcurrentHashMap}, correct for exactly as long as there was
 * one replica — with N, the real budget was N×5 per minute and the limit did not limit. ADR-0024
 * moved them to the database Bucket4j's JDBC {@code ProxyManager} already knew how to use, rather
 * than to the Redis those documents used to promise; the reason is the deployment budget of ADR-0023
 * and it is written down there.
 *
 * <p><strong>The other half of the same fix is {@code forward-headers-strategy}, and neither half
 * works alone.</strong> A shared budget keyed on the wrong address counts every attempt on earth into
 * one bucket. That is why {@code application.yaml} turns on the native forwarded-headers handling in
 * every profile but {@code dev} — with the trusted-proxy list that variant is the only one to have.
 */
@Component
public class LoginAttemptBuckets {

    /** Room for a person who mistypes, none for a script. */
    private static final Bandwidth BURST = Bandwidth.builder()
            .capacity(5)
            .refillIntervally(5, Duration.ofMinutes(1))
            .build();

    /** The one that actually bites: sustained guessing runs out of budget for the hour. */
    private static final Bandwidth SUSTAINED = Bandwidth.builder()
            .capacity(20)
            .refillIntervally(20, Duration.ofHours(1))
            .build();

    private static final BucketConfiguration CONFIGURATION = BucketConfiguration.builder()
            .addLimit(BURST)
            .addLimit(SUSTAINED)
            .build();

    private static final String TABLE = "login_attempt_bucket";

    private static final String PURGE = "DELETE FROM " + TABLE + " WHERE expires_at <= ?";

    private final ProxyManager<String> buckets;
    private final JdbcTemplate jdbc;

    LoginAttemptBuckets(DataSource dataSource, JdbcTemplate jdbc) {
        // Select-for-update and not the advisory-lock variant: an advisory lock is held on the
        // connection and keyed on a number, so a String key has to be hashed into one and two keys
        // that collide would share a budget. Row locking keys on the row, which is what this is.
        //
        // The three column names are passed in because the table is ours: it is created by
        // releases/v1.0/004-login-attempt-bucket.yaml and never by the library (skill `liquibase`,
        // rule 3).
        this.buckets = Bucket4jPostgreSQL.selectForUpdateBasedBuilder(dataSource)
                .primaryKeyMapper(PrimaryKeyMapper.STRING)
                .table(TABLE)
                .idColumn("id")
                .stateColumn("state")
                .expiresAtColumn("expires_at")
                // Zero extra grace: a bucket back at full capacity has no history worth keeping, which
                // is the same condition the in-memory version swept on before v1.0.
                .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ZERO))
                .build();

        // El DataSource lo exige la API de Bucket4j, que gestiona sus propias conexiones. El
        // JdbcTemplate de la purga es el bean que AuthorizationServerConfig ya declara: no hace falta
        // una segunda instancia sobre la misma fuente.
        this.jdbc = jdbc;
    }

    public Bucket forAttempt(String ip, String email) {
        return buckets.builder().build(keyOf(ip, email), () -> CONFIGURATION);
    }

    private static String keyOf(String ip, String email) {
        // Lower-cased because the column is CITEXT and the two spellings are one account — without
        // this, alternating the capitalisation of an email would double the budget.
        return ip + "|" + (email == null ? "" : email.toLowerCase());
    }

    /**
     * Without this the table grows once per distinct {@code IP + email} seen and never shrinks, with a
     * key an attacker chooses — the rate limiter would become the denial of service it exists to
     * prevent. It is the same sweep as before v1.0; what changed is that it now deletes rows instead
     * of map entries, which is why it reads {@code expires_at} rather than the bucket's own state.
     *
     * <p><strong>No {@code @SchedulerLock}, deliberately</strong> (ADR-0024). It is a
     * {@code DELETE … WHERE}: N replicas running it is waste and not a defect, and a lock row rewritten
     * every ten minutes would cost more contention than the delete it guards. The jobs that do carry
     * the lock are the two daily ones, and they carry it as the template for the job that will one day
     * actually need it.
     */
    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    void discardIdleBuckets() {
        jdbc.update(PURGE, System.currentTimeMillis());
    }
}
