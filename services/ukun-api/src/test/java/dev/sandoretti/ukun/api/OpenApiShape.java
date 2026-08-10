package dev.sandoretti.ukun.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Projects an OpenAPI document onto the facets {@link OpenApiDriftIT} compares: a flat, sorted map
 * from a human-readable coordinate to a normalised value.
 *
 * <pre>{@code
 * GET /api/products · parameter size · required                 -> false
 * GET /api/products · response 200 · body.items[].price · type  -> [string]
 * }</pre>
 *
 * <h2>Why a projection and not a diff of the two documents</h2>
 *
 * They cannot be diffed as text, and not only because one is YAML and the other JSON: the contract is
 * hand-written and carries the prose that makes it readable, while springdoc emits a machine's idea of
 * the same API. Comparing them whole would report a hundred differences of wording for every real one.
 * A projection states, once and explicitly, <em>which</em> features of the two documents are the
 * contract — and, by leaving them out, which are not (see {@link OpenApiDriftIT}, "what is not
 * compared").
 *
 * <p>The flat map is also what makes a failure legible: the coordinate names the operation, the
 * parameter or the property and the facet that diverged, so the report is one line per divergence
 * instead of two documents printed side by side.
 *
 * <h2>Normalisation</h2>
 *
 * Both documents are projected through this same class, so anything normalised here is normalised on
 * both sides and cannot hide a divergence on one:
 *
 * <ul>
 *   <li><strong>{@code $ref} is resolved</strong> and {@code allOf} flattened, so a schema is compared
 *       by its shape and not by the name of the component holding it. springdoc names components after
 *       the Java class ({@code ProductSummaryResponse}), the contract names them for the client
 *       ({@code ProductSummary}), and nobody generates a client from {@code /v3/api-docs} — the name is
 *       an artefact, the shape is the contract.
 *   <li><strong>Nullability is a type.</strong> OpenAPI 3.1's {@code type: [string, 'null']} and 3.0's
 *       {@code nullable: true} become the same set of types.
 *   <li><strong>The defaults of {@code style}/{@code explode} are filled in</strong>, so a parameter
 *       that states them and one that relies on the specification's defaults compare equal.
 * </ul>
 */
final class OpenApiShape {

    static final String SEPARATOR = " · ";

    private static final Set<String> HTTP_METHODS =
            Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");

    private final JsonNode document;
    private final Map<String, String> facets = new TreeMap<>();
    private final Map<String, String> operationIds = new TreeMap<>();

    private OpenApiShape(JsonNode document) {
        this.document = document;
    }

    static OpenApiShape of(JsonNode document) {
        OpenApiShape shape = new OpenApiShape(document);
        shape.describe();

        return shape;
    }

    /** The version of the specification the document declares. */
    String version() {
        return document.path("openapi").asText();
    }

    /** Coordinate to value, sorted, for every operation of the document. */
    Map<String, String> facets() {
        return facets;
    }

    /** {@code "GET /api/products"} to {@code "listProducts"}, for every operation of the document. */
    Map<String, String> operationIds() {
        return operationIds;
    }

    private void describe() {
        document.path("paths").properties().forEach(path -> path.getValue().properties().stream()
                .filter(entry -> HTTP_METHODS.contains(entry.getKey()))
                .forEach(entry -> describeOperation(
                        "%s %s".formatted(entry.getKey().toUpperCase(), path.getKey()),
                        entry.getValue(),
                        path.getValue().path("parameters"))));
    }

    private void describeOperation(String operation, JsonNode node, JsonNode inheritedParameters) {
        operationIds.put(operation, node.path("operationId").asText(""));

        put(operation, "operationId", node.path("operationId").asText(""));
        put(operation, "security", securityOf(node));

        describeParameters(operation, inheritedParameters);
        describeParameters(operation, node.path("parameters"));
        describeResponses(operation, node.path("responses"));
    }

    /**
     * The names of the security schemes the operation requires. An operation that declares nothing
     * inherits the document's requirement, and an empty list means "no authentication" — the two ways
     * OpenAPI has of saying the same thing about the six public reads of v0.3.
     */
    private String securityOf(JsonNode operation) {
        JsonNode requirements = operation.has("security") ? operation.get("security") : document.path("security");

        Set<String> schemes = new TreeSet<>();
        requirements.forEach(requirement -> schemes.addAll(names(requirement)));

        return schemes.toString();
    }

    private void describeParameters(String operation, JsonNode parameters) {
        parameters.forEach(declared -> {
            JsonNode parameter = resolve(declared);
            String in = parameter.path("in").asText();
            String coordinate = operation + SEPARATOR + "parameter " + parameter.path("name").asText();

            put(coordinate, "in", in);
            put(coordinate, "required", parameter.path("required").asBoolean(false));
            put(coordinate, "style", styleOf(parameter, in));
            put(coordinate, "explode", explodeOf(parameter, in));

            describeSchema(coordinate + SEPARATOR + "schema", parameter.path("schema"));
        });
    }

    /** The specification's default: {@code form} in the query string and a cookie, {@code simple} elsewhere. */
    private static String styleOf(JsonNode parameter, String in) {
        String byDefault = "query".equals(in) || "cookie".equals(in) ? "form" : "simple";

        return parameter.path("style").asText(byDefault);
    }

    /** The specification's default: {@code true} under {@code form}, {@code false} under any other style. */
    private static boolean explodeOf(JsonNode parameter, String in) {
        return parameter.path("explode").asBoolean("form".equals(styleOf(parameter, in)));
    }

    private void describeResponses(String operation, JsonNode responses) {
        put(operation, "statuses", new TreeSet<>(names(responses)).toString());

        responses.properties().forEach(entry -> {
            String status = entry.getKey();
            JsonNode content = resolve(entry.getValue()).path("content");
            List<String> mediaTypes = names(content);

            put(operation, "response " + status + SEPARATOR + "mediaTypes", new TreeSet<>(mediaTypes).toString());

            // The body of an error is deliberately left out — see OpenApiDriftIT, "what is not compared".
            if (status.startsWith("2")) {
                mediaTypes.forEach(mediaType -> describeSchema(
                        operation + SEPARATOR + "response " + status + body(mediaTypes, mediaType),
                        content.path(mediaType).path("schema")));
            }
        });
    }

    /** The media type only earns a mention when the response offers more than one to tell apart. */
    private static String body(List<String> mediaTypes, String mediaType) {
        return mediaTypes.size() == 1 ? SEPARATOR + "body" : SEPARATOR + "body [" + mediaType + "]";
    }

    /**
     * One coordinate per facet of the schema, recursing into properties ({@code .name}) and into the
     * items of an array ({@code []}), so a divergence points at the property that diverged instead of
     * at the whole payload.
     */
    private void describeSchema(String coordinate, JsonNode declared) {
        JsonNode schema = flatten(declared);

        put(coordinate, "type", typesOf(schema));
        putIfPresent(coordinate, "format", schema.path("format"));
        putIfPresent(coordinate, "enum", schema.path("enum"));

        JsonNode properties = schema.path("properties");
        if (!properties.isMissingNode()) {
            put(coordinate, "required", new TreeSet<>(values(schema.path("required"))).toString());
            properties
                    .properties()
                    .forEach(property -> describeSchema(coordinate + "." + property.getKey(), property.getValue()));
        }

        if (schema.has("items")) {
            describeSchema(coordinate + "[]", schema.get("items"));
        }
    }

    /**
     * The declared types as a set, with {@code null} in it when the value may be absent — whether the
     * document says so the 3.1 way ({@code type: [string, 'null']}) or the 3.0 one
     * ({@code nullable: true}), which is what swagger-core still emits for some annotations.
     */
    private static String typesOf(JsonNode schema) {
        JsonNode type = schema.path("type");

        Set<String> types = new TreeSet<>(type.isArray() ? values(type) : List.of(type.asText("")));
        types.remove("");
        if (schema.path("nullable").asBoolean(false)) {
            types.add("null");
        }

        return types.toString();
    }

    /**
     * The schema with its {@code $ref} resolved and its {@code allOf} merged into a single object, so
     * the contract's {@code allOf: [PageMeta, {items}]} and the flat object springdoc generates from
     * one record compare equal. Properties and {@code required} accumulate across the members; every
     * other keyword is taken from the last member that states it.
     */
    private JsonNode flatten(JsonNode declared) {
        ObjectNode merged = JsonNodeFactory.instance.objectNode();
        mergeInto(merged, declared, new LinkedHashSet<>());

        return merged;
    }

    private void mergeInto(ObjectNode merged, JsonNode declared, Set<String> visited) {
        JsonNode schema = resolve(declared, visited);

        schema.path("allOf").forEach(member -> mergeInto(merged, member, visited));

        schema.properties().stream().filter(entry -> !"allOf".equals(entry.getKey())).forEach(entry -> {
            switch (entry.getKey()) {
                case "properties" -> merged.withObjectProperty("properties").setAll((ObjectNode) entry.getValue());
                case "required" -> merged.withArrayProperty("required").addAll((ArrayNode) entry.getValue());
                default -> merged.set(entry.getKey(), entry.getValue());
            }
        });
    }

    private JsonNode resolve(JsonNode node) {
        return resolve(node, new LinkedHashSet<>());
    }

    /**
     * Follows {@code $ref} inside the same document. The visited set is not defensive programming: the
     * contract is free to declare a recursive schema, and without it a cycle would be a
     * {@code StackOverflowError} instead of a failing assertion.
     */
    private JsonNode resolve(JsonNode node, Set<String> visited) {
        JsonNode resolved = node;
        while (resolved.has("$ref")) {
            String reference = resolved.get("$ref").asText();
            if (!visited.add(reference)) {
                throw new IllegalStateException("Cyclic $ref in the OpenAPI document: " + reference);
            }
            resolved = document.at(reference.substring(1));
        }

        return resolved;
    }

    private static List<String> names(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);

        return names;
    }

    private static List<String> values(JsonNode node) {
        List<String> values = new ArrayList<>();
        node.forEach(value -> values.add(value.asText()));

        return values;
    }

    private void put(String coordinate, String facet, Object value) {
        facets.put(coordinate + SEPARATOR + facet, String.valueOf(value));
    }

    private void putIfPresent(String coordinate, String facet, JsonNode value) {
        if (!value.isMissingNode()) {
            put(coordinate, facet, value.isArray() ? new TreeSet<>(values(value)).toString() : value.asText());
        }
    }
}
