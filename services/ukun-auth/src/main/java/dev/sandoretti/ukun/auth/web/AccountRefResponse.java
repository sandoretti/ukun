package dev.sandoretti.ukun.auth.web;

import java.util.UUID;

/**
 * The body of both the {@code 201} and the {@code 200} of {@code POST /internal/accounts}: the id of
 * the account that holds the email.
 *
 * <p>ukun-api stores it as {@code customer.account_id} — an opaque value with <strong>no foreign
 * key</strong>, because it crosses databases and cannot be expressed as a real one
 * ({@code docs/08-identidad-y-seguridad.md} §1).
 *
 * <p>It carries no {@code created} flag: whether the account was created by this call is the status
 * code's job, and repeating it in the body would be a second answer to one question.
 */
public record AccountRefResponse(UUID accountId) {}
