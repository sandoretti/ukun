package dev.sandoretti.ukun.api.shared.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * The {@code page} and {@code size} query parameters shared by every paginated listing, with the
 * bounds the contract declares in {@code PageParam} and {@code SizeParam}.
 *
 * <p>The upper bound on {@code size} is not decoration: without it, {@code ?size=100000} turns a
 * paginated endpoint back into the unbounded {@code findAll()} that the skill {@code spring-api}
 * bans (rule 8).
 *
 * <p>Defaults are applied in the canonical constructor, so validation runs against the resolved
 * values and {@link #toPageable} never sees a {@code null}.
 */
public record PaginationParams(@Min(0) Integer page, @Min(1) @Max(100) Integer size) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    public PaginationParams {
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
    }

    public Pageable toPageable(Sort sort) {
        return PageRequest.of(page, size, sort);
    }
}
