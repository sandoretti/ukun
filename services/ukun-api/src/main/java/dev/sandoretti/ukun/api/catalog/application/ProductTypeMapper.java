package dev.sandoretti.ukun.api.catalog.application;

import dev.sandoretti.ukun.api.catalog.domain.ProductType;
import org.mapstruct.Mapper;

/**
 * {@code ProductType} to its DTO, with MapStruct like every other mapping in the service (ADR-0009).
 *
 * <p>A mapper of its own and not a method on {@link ProductMapper}. The unit is the aggregate, and
 * {@code product_type} is one: it has its own repository, its own read side, its own URL at the root
 * of the contract ({@code /api/product-types}, alongside {@code /api/stores}, as closed reference
 * data) and its own CRUD in RF-13. Sharing a mapper would have made
 * {@link ProductTypeQueryService} depend on the product mapper to call a single two-field method, and
 * the two would then have grown together for no reason — the RF-13 write side is coming to this one
 * and nothing about it belongs next to a catalogue row.
 *
 * <p>It is <strong>not</strong> what {@link ProductMapper} uses to fill {@code typeId}/{@code typeName}
 * on a product: those are flattened straight off the association, so nothing here is on the listing's
 * path and the two mappers do not reference each other.
 *
 * <p>No {@code componentModel}, no {@code unmappedTargetPolicy} and no {@code uses}: the first two come
 * from the {@code compilerArgs} of this service's {@code build.gradle.kts}, and there is no collaborator to inject —
 * both fields map by name. That is safe to leave implicit precisely because
 * {@code unmappedTargetPolicy = ERROR} turns a component without a source into a compile error.
 */
@Mapper
public interface ProductTypeMapper {

    ProductTypeResponse toResponse(ProductType productType);
}
