package dev.sandoretti.ukun.api.catalog.application;

import dev.sandoretti.ukun.api.catalog.domain.Product;
import dev.sandoretti.ukun.api.catalog.domain.ProductType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Entity to DTO, with MapStruct and not a hand-written static factory (ADR-0009, which reverses an
 * earlier decision in this repo). {@code ModelMapper} and {@code BeanUtils.copyProperties} stay
 * banned: reflection-based mapping fails at runtime, which is the failure mode this rule exists to
 * avoid.
 *
 * <p>It carries no {@code componentModel} and no {@code unmappedTargetPolicy} of its own — both come
 * from the {@code compilerArgs} of the root {@code pom.xml}, so every mapper in the service is
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
     * Both names match, so there is nothing to declare — and {@code unmappedTargetPolicy = ERROR} is
     * what makes that safe to leave implicit: adding a component to the record without a source for it
     * breaks the build.
     *
     * <p>It lives in this mapper and not in a {@code ProductTypeMapper} of its own because MapStruct's
     * unit is the aggregate, not the entity: {@code product} and {@code product_type} are the two
     * tables the {@code catalog} module owns, and a second interface for a two-field record would be
     * one more bean to find and nothing else.
     */
    ProductTypeResponse toType(ProductType productType);

    /**
     * The typeahead row. It maps {@code imageUrl} and nothing about the type, which is the whole point:
     * touching {@code productType} here would fetch an association the response does not carry, on the
     * one endpoint that runs on every keystroke.
     */
    @Mapping(target = "imageUrl", source = "imageKey", qualifiedByName = "toImageUrl")
    ProductSuggestionResponse toSuggestion(Product product);
}
