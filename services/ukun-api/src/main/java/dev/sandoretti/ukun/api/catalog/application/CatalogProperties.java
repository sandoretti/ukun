package dev.sandoretti.ukun.api.catalog.application;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Typed configuration of the catalog module. Today it holds one value, and it exists as configuration
 * rather than as a constant for the reason {@code docs/03-analisis-y-patrones.md} §2 gives when it
 * fixes defect #6: the legacy hardcodes {@code "/opt/resources/uploads/"} in the middle of a service.
 *
 * <p>{@code mediaBasePath} is the public prefix product images are served from. {@code product.image_key}
 * is a storage key and is never exposed ({@code docs/08-identidad-y-seguridad.md} §9) — the API composes
 * a servable URL from it. The prefix is a deployment fact: {@code /media/**} is a public route of the
 * gateway and the adapter behind it changes in v1.1 when the filesystem becomes object storage, at
 * which point this value changes and no Java code does.
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "ukun.catalog")
public class CatalogProperties {

    private String mediaBasePath = "/media/products/";
}
