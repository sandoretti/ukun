package dev.sandoretti.ukun.api.customers.application;

import dev.sandoretti.ukun.api.customers.CustomerSnapshot;
import dev.sandoretti.ukun.api.customers.domain.Customer;
import dev.sandoretti.ukun.api.customers.domain.CustomerAddress;
import dev.sandoretti.ukun.api.customers.domain.CustomerCard;
import dev.sandoretti.ukun.api.customers.domain.ProfileLocale;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * The profile in both directions, with MapStruct like every mapping in this service (ADR-0009).
 *
 * <p>Reading is the ordinary case. Writing is the one worth explaining: the three update methods take
 * a {@code @MappingTarget}, so they edit the rows the customer already owns instead of building new
 * ones. Replacing the address with a fresh {@code CustomerAddress} would orphan a row that
 * {@code ON DELETE RESTRICT} will not let go of, and — with the cascade of {@link Customer} — would
 * quietly change which row every past order points at, if orders pointed at it. They do not, and
 * deliberately: ADR-0011 copies the address into {@code customer_order} precisely so that editing a
 * profile cannot rewrite the shipping address of a past order (defect #19).
 *
 * <p>{@code unmappedTargetPolicy = ERROR} arrives through {@code compilerArgs}, so {@code @Mapper} goes
 * bare. It is what makes the ignores below a decision each: the day {@code customer} grows a column,
 * this file stops compiling until somebody says where its value comes from.
 */
@Mapper
public interface ProfileMapper {

    /**
     * {@code email} is mapped from the parameter and not from the entity, and the explicit
     * {@code @Mapping} is what says so — both sources offer one, and MapStruct would otherwise refuse
     * to guess. The parameter is the token's claim, which is current by construction; the column is a
     * projection of it ({@code docs/08-identidad-y-seguridad.md} §2).
     */
    @Mapping(target = "email", source = "email")
    CustomerProfileResponse toResponse(Customer customer, String email);

    /**
     * The profile as checkout copies it (RF-04, ADR-0011). Flattening two associations into eight
     * values is exactly what the {@code source} paths say, and {@code unmappedTargetPolicy = ERROR}
     * is what would break the build if the record grew a ninth nobody wired.
     *
     * <p>{@code cardBrand} crosses as its {@code name()}: {@link CustomerSnapshot} explains why the
     * enum itself does not cross a module boundary.
     */
    @Mapping(target = "street", source = "address.street")
    @Mapping(target = "city", source = "address.city")
    @Mapping(target = "province", source = "address.province")
    @Mapping(target = "postalCode", source = "address.postalCode")
    @Mapping(target = "cardLast4", source = "card.last4")
    @Mapping(target = "cardBrand", source = "card.brand")
    @Mapping(target = "cardExpMonth", source = "card.expMonth")
    @Mapping(target = "cardExpYear", source = "card.expYear")
    CustomerSnapshot toSnapshot(Customer customer);

    ProfileAddress toAddress(CustomerAddress address);

    ProfileCard toCard(CustomerCard card);

    /**
     * {@code address} and {@code card} are ignored here and applied by their own methods, for the same
     * reason the signup assembles them by hand: they are separate rows with separate identities, and
     * flattening them into this mapping would mean MapStruct deciding on its own whether to update
     * them or replace them.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "accountId", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "address", ignore = true)
    @Mapping(target = "card", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void apply(UpdateProfileRequest request, @MappingTarget Customer customer);

    @Mapping(target = "id", ignore = true)
    void apply(ProfileAddress request, @MappingTarget CustomerAddress address);

    @Mapping(target = "id", ignore = true)
    void apply(ProfileCard request, @MappingTarget CustomerCard card);

    /** The column holds {@code 'es'}; Java names the constant {@code ES}. See {@link ProfileLocale}. */
    default ProfileLocale toLocale(String code) {
        return code == null ? null : ProfileLocale.of(code);
    }

    default String toCode(ProfileLocale locale) {
        return locale == null ? null : locale.code();
    }
}
