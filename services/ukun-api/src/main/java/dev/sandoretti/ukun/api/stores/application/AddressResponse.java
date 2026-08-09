package dev.sandoretti.ukun.api.stores.application;

/**
 * The {@code Address} schema of the contract, nested inside {@link StoreResponse}.
 *
 * <p>One country for now, so it carries no country field — the contract says as much, and
 * {@code ck_address_postal_code} already assumes a Spanish five-digit code.
 *
 * <p>It is a DTO of the {@code stores} module and not a shared type: the profile address of RF-05
 * (v0.5) belongs to {@code customers}, is written as well as read, and will carry its own validation.
 * Two records with the same four fields is the cheaper of the two mistakes available here — the other
 * one is a shared DTO that two modules pull in opposite directions.
 */
public record AddressResponse(String street, String city, String province, String postalCode) {}
