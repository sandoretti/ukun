package dev.sandoretti.ukun.api.customers.application;

import dev.sandoretti.ukun.api.customers.domain.Customer;
import dev.sandoretti.ukun.api.customers.domain.CustomerAddress;
import dev.sandoretti.ukun.api.customers.domain.CustomerCard;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * The signup form to the three rows it becomes, with MapStruct like every mapping in this service
 * (ADR-0009 — which reversed an earlier decision in this same repo in favour of hand-written static
 * factories, so writing one here would be walking that back by hand).
 *
 * <p>This is the first mapper in the service that maps <em>into</em> an entity rather than out of one,
 * and it is where {@code unmappedTargetPolicy = ERROR} pays for itself: every column the request does
 * not carry has to be named below. That turns "we forgot to set {@code accountId}" — a customer with
 * no owner — from a runtime constraint violation into a compile error, and it means the day
 * {@code customer} grows a column, this file stops compiling until somebody decides where its value
 * comes from.
 *
 * <p>The ignored targets fall into three groups, and none of them is an oversight:
 *
 * <ul>
 *   <li>{@code id}, {@code createdAt}, {@code updatedAt} — written by the database.
 *   <li>{@code locale} — defaulted by the column; signup does not ask for it (ADR-0019).
 *   <li>{@code address}, {@code card} — mapped by their own methods and assembled by the caller, so
 *       that the nested records do not have to be flattened into {@code Customer} by name.
 * </ul>
 */
@Mapper
public interface SignupMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "locale", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "address", ignore = true)
    @Mapping(target = "card", ignore = true)
    Customer toCustomer(SignupRequest request, UUID accountId);

    @Mapping(target = "id", ignore = true)
    CustomerAddress toAddress(ProfileAddress request);

    @Mapping(target = "id", ignore = true)
    CustomerCard toCard(ProfileCard request);
}
