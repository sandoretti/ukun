package dev.sandoretti.ukun.api.staff.application;

import dev.sandoretti.ukun.api.staff.domain.Employee;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * The employee's two mappings, with MapStruct and not hand-written factories (ADR-0009). Bare
 * {@code @Mapper}: {@code componentModel} and {@code unmappedTargetPolicy = ERROR} arrive through the
 * service's {@code compilerArgs}.
 *
 * <p>Both directions name {@code isAdmin} explicitly, and that is the whole reason the annotations are
 * here: the column is {@code is_admin}, the entity field is {@code admin} (Lombok's accessor for a
 * {@code boolean} drops the prefix) and the contract's property is {@code isAdmin}. Without the
 * mapping, {@code unmappedTargetPolicy = ERROR} turns that mismatch into a compile error rather than
 * into a response where every employee looks like an ordinary one.
 */
@Mapper
public interface EmployeeMapper {

    @Mapping(target = "isAdmin", source = "admin")
    EmployeeResponse toResponse(Employee employee);

    /**
     * The response of the alta, built from what was just written instead of read back: the row's
     * fields are exactly the request's plus the two the server decided ({@code id} and the
     * {@code storeId} resolved from the admin), so a second {@code SELECT} would return the same
     * values it already has.
     */
    @Mapping(target = "isAdmin", source = "request.isAdmin")
    EmployeeResponse toResponse(Long id, CreateEmployeeRequest request, Long storeId);

    /**
     * The alta's row. Every target the request does not carry is named below, which is what
     * {@code unmappedTargetPolicy = ERROR} buys: the day {@code employee} grows a column, this file
     * stops compiling until somebody decides where its value comes from.
     *
     * <p>{@code accountId} and {@code storeId} are parameters and not fields of the request on purpose
     * — they are the two values a client must never be able to choose ({@code docs/08} §3).
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "admin", source = "request.isAdmin")
    Employee toEmployee(CreateEmployeeRequest request, UUID accountId, Long storeId);
}
