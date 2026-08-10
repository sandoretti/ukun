import { http, HttpResponse } from 'msw';

import type { components } from '../../types/api';

/**
 * Test-only stand-in for `GET /api/stores`, typed from the same generated OpenAPI types the app
 * uses. It lives apart from `catalogHandlers` because two features read it: the stores screen lists
 * them and the catalog resolves the online one to ask for availability.
 */

type Store = components['schemas']['Store'];

const base = import.meta.env.VITE_API_URL || '';

/**
 * Seed data **already sorted by name**, like the contract promises — the screen must not reorder it.
 * Exactly one row has `isOnline` set (`ux_store_online`), and its address is a warehouse rather than
 * a shop: it is the store orders are served from, not one to visit.
 *
 * Two things the shape is deliberately built to prove: accented city names, so the filter has to
 * ignore diacritics the way the API's `unaccent(lower(...))` does, and a store whose city does not
 * appear in its own name (`Ukun Online` in Llanera), so a match by city cannot pass by accident as a
 * match by name.
 *
 * Identifiers 1 and 2 are fixed: the availability stock map of `catalogHandlers` is keyed by them.
 */
export const stores: Store[] = [
  {
    id: 3,
    name: 'Ukun Avilés',
    isOnline: false,
    phone: '+34900000003',
    address: { street: 'Calle La Cámara 8', city: 'Avilés', province: 'Asturias', postalCode: '33402' },
  },
  {
    id: 2,
    name: 'Ukun Gijón',
    isOnline: false,
    phone: '+34900000002',
    address: { street: 'Avenida de la Costa 22', city: 'Gijón', province: 'Asturias', postalCode: '33205' },
  },
  {
    id: 4,
    name: 'Ukun León',
    isOnline: false,
    phone: '+34900000004',
    address: { street: 'Avenida de Ordoño II 15', city: 'León', province: 'León', postalCode: '24001' },
  },
  {
    id: 1,
    name: 'Ukun Online',
    isOnline: true,
    phone: '+34900000001',
    address: { street: 'Polígono de Asipo, nave 12', city: 'Llanera', province: 'Asturias', postalCode: '33428' },
  },
  {
    id: 5,
    name: 'Ukun Oviedo',
    isOnline: false,
    phone: '+34900000005',
    address: { street: 'Calle Uría 1', city: 'Oviedo', province: 'Asturias', postalCode: '33003' },
  },
];

/** The mock's stand-in for the database's `unaccent(lower(...))`. */
const normalise = (value: string) => value.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase();

export const storeHandlers = [
  /**
   * `q` is part of the contract and the mock honours it, but the stores screen never sends it: it is
   * closed reference data, and filtering on the cached list is what keeps a single cache entry alive
   * for the whole session instead of one per typed term.
   */
  http.get(`${base}/api/stores`, ({ request }) => {
    const term = new URL(request.url).searchParams.get('q');
    if (term === null) {
      return HttpResponse.json(stores);
    }

    const needle = normalise(term);
    const matched = stores.filter(
      (store) => normalise(store.name).includes(needle) || normalise(store.address.city).includes(needle),
    );
    return HttpResponse.json(matched);
  }),
];
