package dev.sandoretti.ukun.api.stores.application;

import dev.sandoretti.ukun.api.stores.domain.Store;
import dev.sandoretti.ukun.api.stores.domain.StoreAddress;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * {@code Store} to its DTO, with MapStruct like every other mapping in the service (ADR-0009).
 *
 * <p>No {@code componentModel} and no {@code unmappedTargetPolicy}: both come from the
 * {@code compilerArgs} of this service's {@code build.gradle.kts}, which is also what makes a forgotten field a
 * compile error instead of a {@code null} in the response.
 *
 * <p>The nested {@link AddressResponse} is mapped by a second method in the same interface rather
 * than by four {@code @Mapping(source = "address.street", …)} lines: MapStruct picks it up for the
 * {@code address} property on its own, and the flattened version would have to be repeated in full
 * the day a second store DTO appears.
 */
@Mapper
public interface StoreMapper {

    /**
     * The one explicit mapping is {@code isOnline}, and it is a naming collision rather than a
     * transformation. Lombok generates {@code isOnline()} for the field {@code isOnline}, from which
     * MapStruct derives the JavaBeans property {@code online}; the record component the contract asks
     * for is {@code isOnline}, whose accessor is {@code isOnline()} verbatim. The two names never meet,
     * and {@code unmappedTargetPolicy = ERROR} turns that into a compile error instead of a store that
     * silently reports itself as not being the online one.
     */
    @Mapping(target = "isOnline", source = "online")
    StoreResponse toResponse(Store store);

    AddressResponse toResponse(StoreAddress address);
}
