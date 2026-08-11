import type { components } from '../../../types/api';

type Address = components['schemas']['Address'];

type AddressBlockProps = {
  /** Already translated by the caller: checkout says where it *will* ship, an order where it did. */
  title: string;
  address: Address;
};

/**
 * A postal address, read-only. The same block serves the checkout —where it is the profile's current
 * address, the one the order is about to copy (RF-04)— and an order, where it is the immutable
 * snapshot taken at confirmation time (ADR-0011). One country for now, so no country line.
 *
 * It is an `<address>`, which is the element for exactly this, and the parts are separate lines
 * rather than one string joined with commas: a postal address is read line by line.
 */
export function AddressBlock({ title, address }: AddressBlockProps) {
  return (
    <section>
      <h2 className="h6 text-body-secondary mb-2">{title}</h2>
      {/* Each line is its own element rather than text separated by `<br>`: that is what lets the
          street be read —and asserted— as one line instead of as part of a single run of text. */}
      <address className="mb-0">
        <span className="d-block">{address.street}</span>
        <span className="d-block">
          {address.postalCode} {address.city}
        </span>
        <span className="d-block">{address.province}</span>
      </address>
    </section>
  );
}
