package dev.sandoretti.ukun.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.otlp.OtlpMetricsProperties;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * That the two properties which switch OTLP export on are spelled the way Boot 4 spells them.
 *
 * <p>This is the one failure of this release that produces no error of any kind. Boot 4 moved the
 * tracing exporter to {@code management.opentelemetry.tracing.export.otlp.*} and left the metrics one
 * at {@code management.otlp.metrics.export.*}; writing the Boot 3.x name for either is not a startup
 * failure, not a warning and not a log line — the unknown property is ignored, the binder leaves the
 * field null, and the service runs perfectly while exporting nothing. Nobody notices until someone
 * goes looking for a trace that was never sent.
 *
 * <p>So what is asserted is the <em>binding</em>: the value given here has to come back out of the
 * bound properties object. Asserting that an exporter bean exists would prove less than it appears
 * to — those beans have defaults and are present either way.
 *
 * <p>The complementary half, that a service with neither property set starts up normally and exports
 * nothing, needs no test of its own: every other {@code *IT} in this suite is that test, since none of
 * them defines either property. That is also the behaviour production depends on, because the endpoint
 * arrives from the environment (compose.dev.yaml locally, the deployment in ADR-0023) and is absent
 * whenever there is no collector to send to.
 *
 * <p>Metrics need their own switch and traces do not, which looks inconsistent until you look at the
 * defaults: the trace exporter is {@code @ConditionalOnProperty} on its endpoint, so no endpoint means
 * no exporter, while {@code management.otlp.metrics.export.enabled} ships as {@code true} and
 * Micrometer falls back to {@code localhost:4318} when no URL is given. Left alone, that second one
 * would have every test run and every PR posting to a closed port once a minute.
 */
@SpringBootTest(
        properties = {
            "management.opentelemetry.tracing.export.otlp.endpoint=http://otel-collector.test:4318/v1/traces",
            "management.otlp.metrics.export.url=http://otel-collector.test:4318/v1/metrics",
            // Sin esto no hay ni siquiera un OtlpMetricsProperties que inyectar, y el test falla al
            // arrancar el contexto en vez de al comparar. Es la prueba, por la vía incómoda, de que la
            // línea `enabled: false` de application.yaml hace lo que dice: apaga la autoconfiguración
            // entera, no solo el envío.
            "management.otlp.metrics.export.enabled=true"
        })
class OtlpExportersIT extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        useDatabase(registry, SCHEMA_ONLY_DATABASE);
    }

    @Autowired
    private OtlpTracingProperties tracing;

    @Autowired
    private OtlpMetricsProperties metrics;

    @Test
    @DisplayName("binds the trace endpoint from the Boot 4 property name")
    void bindsTheTraceEndpoint() {
        assertThat(tracing.getEndpoint()).isEqualTo("http://otel-collector.test:4318/v1/traces");
    }

    @Test
    @DisplayName("binds the metrics url from the Boot 4 property name")
    void bindsTheMetricsUrl() {
        assertThat(metrics.getUrl()).isEqualTo("http://otel-collector.test:4318/v1/metrics");
    }
}
