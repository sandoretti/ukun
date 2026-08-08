package dev.sandoretti.ukun.api.shared.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * The pagination wrapper of the contract ({@code PageMeta} in {@code docs/api/openapi/ukun-api.yaml}),
 * shared by the five open-ended listings.
 *
 * <p>It exists precisely so the response is <strong>not</strong> Spring Data's default {@code Page<T>}
 * serialisation. The frontend types this shape with {@code openapi-typescript}, and it must survive
 * Spring changing how it serialises a page tomorrow — which is why {@link #of} maps the two
 * explicitly instead of letting Jackson walk a {@code Page}.
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> toItem) {
        return new PageResponse<>(
                page.getContent().stream().map(toItem).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
