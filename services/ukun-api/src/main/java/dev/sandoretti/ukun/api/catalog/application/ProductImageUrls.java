package dev.sandoretti.ukun.api.catalog.application;

import org.mapstruct.Named;
import org.springframework.stereotype.Component;

/**
 * Turns a stored {@code product.image_key} into the URL a browser can actually request.
 *
 * <p>It is a collaborator of {@link ProductMapper} rather than a method inside it so the mapper can
 * stay an interface and receive this by constructor: MapStruct injects the types listed in
 * {@code uses} the way {@code -Amapstruct.defaultInjectionStrategy=constructor} says, and field
 * injection disappears from the generated code.
 *
 * <p>The key never leaves the service ({@code docs/08-identidad-y-seguridad.md} §9). It is an
 * internal detail of {@code FileStoragePort} and changes shape in v1.1, when the filesystem adapter
 * becomes object storage — at which point only {@link CatalogProperties} changes.
 */
@Component
public class ProductImageUrls {

    private final CatalogProperties properties;

    ProductImageUrls(CatalogProperties properties) {
        this.properties = properties;
    }

    /**
     * The photo is optional (RF-10), so a product without one gets {@code null} — which the contract
     * allows — rather than a URL pointing at nothing.
     */
    @Named("toImageUrl")
    public String toImageUrl(String imageKey) {
        return imageKey == null ? null : properties.getMediaBasePath() + imageKey;
    }
}
