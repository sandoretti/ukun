package dev.sandoretti.ukun.api.staff.application;

import dev.sandoretti.ukun.api.identity.RegistrationOutcome;

/**
 * The employee that was created and how far its account got — the two things the controller needs to
 * choose between the {@code 201} and the {@code 202} of the contract.
 *
 * <p>The employee is present in both cases, because in both cases the row exists. What
 * {@code ACTIVATION_PENDING} means is that the account cannot be logged into yet, and that repeating
 * the same alta finishes the job.
 */
public record EmployeeCreation(EmployeeResponse employee, RegistrationOutcome outcome) {}
