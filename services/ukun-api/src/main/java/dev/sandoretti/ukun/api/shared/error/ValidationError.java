package dev.sandoretti.ukun.api.shared.error;

/**
 * One entry of the {@code errors[]} extension of {@code VALIDATION_FAILED}. The frontend paints each
 * one next to the form field named by {@code field}.
 *
 * <p>{@code message} is fixed English for the operator, like {@code title} and {@code detail}: the
 * text the user reads is resolved in the frontend from the {@code code} (ADR-0019), never from here.
 */
public record ValidationError(String field, String message) {}
