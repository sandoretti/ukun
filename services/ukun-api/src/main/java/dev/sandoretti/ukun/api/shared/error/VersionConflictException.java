package dev.sandoretti.ukun.api.shared.error;

import java.util.Map;

/**
 * A write that lost the race against another one on the same row — the optimistic concurrency of
 * ADR-0008, translated into the contract.
 *
 * <p>It carries the same two extensions as {@link ResourceNotFoundException} on purpose: the client's
 * next move is to read that exact resource again and retry with the version it gets back, and it can
 * only do that if the response names the resource rather than saying "something changed".
 *
 * <p>It lives in {@code shared} rather than in {@code inventory}, where the only thrower is today,
 * because {@code VERSION_CONFLICT} is not a fact about stock: it is what this API answers whenever an
 * {@code @Version} check fails. Putting it next to its first caller would mean the second one either
 * imports across a module boundary or writes the same class again.
 */
public final class VersionConflictException extends UkunException {

    private final String resource;
    private final String id;

    public VersionConflictException(String resource, Object id) {
        super("%s %s was modified by another request".formatted(resource, id));
        this.resource = resource;
        this.id = String.valueOf(id);
    }

    @Override
    public ErrorCode getCode() {
        return ErrorCode.VERSION_CONFLICT;
    }

    @Override
    public Map<String, Object> getExtensions() {
        return Map.of("resource", resource, "id", id);
    }
}
