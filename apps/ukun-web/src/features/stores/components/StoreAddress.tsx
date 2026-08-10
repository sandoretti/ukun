import type { Store } from '../../../shared/api/stores';

type StoreAddressProps = {
  address: Store['address'];
  /** Spacing and colour belong to whoever places it: the card and the panel sit on different grounds. */
  className?: string;
};

/**
 * The postal address of a store. Shared by the grid card and the online panel, which is why it is a
 * component and not the same six lines written twice.
 *
 * `address` is the element for exactly this, and Bootstrap's reboot already keeps it from being
 * italicised. One element per line instead of `<br>`: the break belongs to how the address is laid
 * out, not to its content, and it keeps each line a node of its own.
 *
 * Spanish order and a five-digit postal code, with no country — it is what the contract's `Address`
 * carries. It is deliberately **not** translated: the format of an address belongs to the country it
 * is in, not to the language of whoever is reading it.
 */
export function StoreAddress({ address, className }: StoreAddressProps) {
  const { street, postalCode, city, province } = address;

  return (
    <address className={className}>
      <span className="d-block">{street}</span>
      <span className="d-block">{`${postalCode} ${city}`}</span>
      <span className="d-block">{province}</span>
    </address>
  );
}
