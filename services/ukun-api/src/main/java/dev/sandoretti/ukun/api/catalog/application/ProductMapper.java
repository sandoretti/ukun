package dev.sandoretti.ukun.api.catalog.application;

import dev.sandoretti.ukun.api.catalog.domain.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Entity to DTO, with MapStruct and not a hand-written static factory (ADR-0009, which reverses an
 * earlier decision in this repo). {@code ModelMapper} and {@code BeanUtils.copyProperties} stay
 * banned: reflection-based mapping fails at runtime, which is the failure mode this whole rule
 * exists to avoid.
 *
 * <p>It carries no {@code componentModel} and no {@code unmappedTargetPolicy} of its own — both come
 * from the {@code compilerArgs} of the root {@code pom.xml}, so every mapper in the service is
 * generated as a Spring bean and any unmapped target field breaks the build instead of silently
 * serialising as {@code null}.
 *
 * <p>An abstract class rather than an interface because composing {@code imageUrl} needs
 * {@link CatalogProperties}; MapStruct generates a {@code @Component} subclass and Spring injects it.
 *
 * <p>It lives in {@code application/} and not in {@code web/} on purpose: the generated
 * implementation reads the entity, and {@code ..web..} is the package the ArchUnit rule
 * "no class in a web package reaches for {@code jakarta.persistence}" watches.
 */
@Mapper
public abstract class ProductMapper {

    @Autowired
    protected CatalogProperties properties;

    @Mapping(target = "typeId", source = "productType.id")
    @Mapping(target = "typeName", source = "productType.name")
    @Mapping(target = "imageUrl", source = "imageKey", qualifiedByName = "toImageUrl")
    public abstract ProductSummaryResponse toSummary(Product product);

    /**
     * The photo is optional (RF-10), so a product without one gets {@code null} — which the contract
     * allows — rather than a URL pointing at nothing.
     */
    @Named("toImageUrl")
    protected String toImageUrl(String imageKey) {
        return imageKey == null ? null : properties.getMediaBasePath() + imageKey;
    }
}
