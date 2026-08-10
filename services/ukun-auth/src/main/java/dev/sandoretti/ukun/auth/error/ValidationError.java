package dev.sandoretti.ukun.auth.error;

/**
 * One entry of the {@code errors[]} extension of {@code VALIDATION_FAILED}. ukun-api propagates it
 * unchanged, and the frontend paints it next to the form field named by {@code field} — which is why
 * a rejected password reports {@code field: "password"} and not a message about "the request".
 *
 * <p>{@code message} is fixed English for the operator; the text the user reads is resolved in the
 * frontend from the {@code code} (ADR-0019).
 */
public record ValidationError(String field, String message) {}
