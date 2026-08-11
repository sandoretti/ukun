package dev.sandoretti.ukun.api.orders.application;

/**
 * The {@code Address} schema of the contract, as an order carries it: where this order was sent.
 *
 * <p>A record of this module and not a shared DTO, the same call {@code ProfileAddress} made — two
 * records with the same four fields is the cheaper of the two mistakes available, the other being a
 * shared type two modules pull in opposite directions. Here the two are not even the same thing: that
 * one is a profile field somebody edits, this one is a copy frozen at confirmation time (ADR-0011).
 *
 * <p>Read-only, so it carries no validation annotations: nothing in this service accepts an address on
 * an order — it is taken from the profile, never sent by the client.
 */
public record OrderAddressResponse(String street, String city, String province, String postalCode) {}
