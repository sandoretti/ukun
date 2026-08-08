package dev.sandoretti.ukun.api.shared.error;

import java.util.Map;

/**
 * {@code RESOURCE_NOT_FOUND} (404) with its {@code resource} and {@code id} extensions. Thrown from
 * the {@code orElseThrow} of a service, which is what replaces the legacy pattern of returning
 * {@code null} and testing for it in every controller (defect #11).
 *
 * <p>{@code id} is text, not a number: the registry types the extension as a string so the same
 * exception serves numeric identifiers, SKUs and UUIDs.
 */
public final class ResourceNotFoundException extends UkunException {

    private final String resource;
    private final String id;

    public ResourceNotFoundException(String resource, Object id) {
        super("%s %s does not exist".formatted(resource, id));
        this.resource = resource;
        this.id = String.valueOf(id);
    }

    @Override
    public ErrorCode getCode() {
        return ErrorCode.RESOURCE_NOT_FOUND;
    }

    @Override
    public Map<String, Object> getExtensions() {
        return Map.of("resource", resource, "id", id);
    }
}
