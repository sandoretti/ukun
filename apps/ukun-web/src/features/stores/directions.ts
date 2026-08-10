import type { Store } from '../../shared/api/stores';

/**
 * Where «how to get there» goes. The contract carries no coordinates — `Address` is street, city,
 * province and postal code — so this is a **search** by the address the API actually returned, not a
 * pin at a latitude nobody sent. Google's documented cross-platform URL, which needs no API key and
 * hands over to the native app on a phone.
 *
 * The day the contract grows coordinates, this is the one place that changes.
 */
export function directionsUrl(store: Store): string {
  const { street, postalCode, city, province } = store.address;
  const query = `${store.name}, ${street}, ${postalCode} ${city}, ${province}`;
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(query)}`;
}
