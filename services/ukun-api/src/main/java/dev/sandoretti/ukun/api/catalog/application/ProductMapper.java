package dev.sandoretti.ukun.api.catalog.application;

import dev.sandoretti.ukun.api.catalog.ProductSummary;
import dev.sandoretti.ukun.api.catalog.domain.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Entity to DTO, with MapStruct and not a hand-written static factory (ADR-0009, which reverses an
 * earlier decision in this repo). {@code ModelMapper} and {@code BeanUtils.copyProperties} stay
 * banned: reflection-based mapping fails at runtime, which is the failure mode this rule exists to
 * avoid.
 *
 * <p>It carries no {@code componentModel} and no {@code unmappedTargetPolicy} of its own — both come
 * from the {@code compilerArgs} of this service's {@code build.gradle.kts}, so every mapper in the service is
 * generated as a Spring bean and any unmapped target field breaks the build instead of silently
 * serialising as {@code null}.
 *
 * <p>An interface with a collaborator in {@code uses}, not an abstract class holding an
 * {@code @Autowired} field: constructor injection is the rule (skill {@code java-springboot}), and
 * the generated implementation follows it because the root POM sets
 * {@code -Amapstruct.defaultInjectionStrategy=constructor}.
 *
 * <p>It lives in {@code application/} and not in {@code web/} on purpose: the generated
 * implementation reads the entity, and {@code ..web..} is the package the ArchUnit rule "no class in
 * a web package reaches for {@code jakarta.persistence}" watches.
 */
@Mapper(uses = ProductImageUrls.class)
public interface ProductMapper {

    @Mapping(target = "typeId", source = "productType.id")
    @Mapping(target = "typeName", source = "productType.name")
    @Mapping(target = "imageUrl", source = "imageKey", qualifiedByName = "toImageUrl")
    ProductSummaryResponse toSummary(Product product);

    /**
     * The same seven fields plus {@code description}, which MapStruct maps by name without being told.
     * The three explicit {@code @Mapping}s are repeated rather than factored out into a shared
     * {@code @Mapping} constant: annotations cannot be inherited between methods of an interface, and
     * {@code unmappedTargetPolicy = ERROR} makes forgetting one a compile error rather than a
     * {@code null} in the response.
     */
    @Mapping(target = "typeId", source = "productType.id")
    @Mapping(target = "typeName", source = "productType.name")
    @Mapping(target = "imageUrl", source = "imageKey", qualifiedByName = "toImageUrl")
    ProductDetailResponse toDetail(Product product);

    /**
     * The typeahead row. It maps {@code imageUrl} and nothing about the type, which is the whole point:
     * touching {@code productType} here would fetch an association the response does not carry, on the
     * one endpoint that runs on every keystroke.
     */
    @Mapping(target = "imageUrl", source = "imageKey", qualifiedByName = "toImageUrl")
    ProductSuggestionResponse toSuggestion(Product product);

    /**
     * What another module sees of a product ({@link ProductSummary}), and the only method here whose
     * target is not a response of this module's own endpoints.
     *
     * <p>Five fields mapped by name and not one {@code @Mapping}: the record was named after the entity's
     * properties on purpose, so that a column renamed tomorrow breaks the build here rather than
     * silently mapping to {@code null} — which {@code unmappedTargetPolicy = ERROR} guarantees only for
     * targets nobody wrote. It touches neither {@code productType} nor {@code imageKey}: the crossing is
     * a batch read of many rows, and an association fetched per row is the N+1 that ADR-0017 §2 asks
     * this boundary to make impossible.
     */
    ProductSummary toModuleSummary(Product product);
}
