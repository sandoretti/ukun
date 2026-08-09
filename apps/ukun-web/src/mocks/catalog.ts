import { http, HttpResponse } from 'msw';

import type { components } from '../types/api';

type ProductSummary = components['schemas']['ProductSummary'];
type ProductType = components['schemas']['ProductType'];
type ProductPage = components['schemas']['ProductPage'];
type Store = components['schemas']['Store'];
type AvailabilityResponse = components['schemas']['AvailabilityResponse'];
type ProblemDetail = components['schemas']['ProblemDetail'];

const base = import.meta.env.VITE_API_URL || '';

export const productTypes: ProductType[] = [
  { id: 1, name: 'Sofás' },
  { id: 2, name: 'Camas' },
  { id: 3, name: 'Mesas' },
  { id: 4, name: 'Sillas' },
  { id: 5, name: 'Iluminación' },
];

const typeName = (id: number) => productTypes.find((type) => type.id === id)!.name;

const product = (
  id: number,
  sku: string,
  name: string,
  price: string,
  typeId: number,
  imageUrl: string | null = `/media/products/${id}.jpg`,
): ProductSummary => ({ id, sku, name, price, typeId, typeName: typeName(typeId), imageUrl });

/**
 * Seed data deliberately shaped like the demo database: accented names (so the name filter has to
 * ignore accents, defect #22) and a real price tie at `399.99` between two products (so ordering by
 * price has to break the tie by `id`, defect #23).
 */
export const products: ProductSummary[] = [
  product(1, 'SOFA-CUERO-01', 'Sofá de cuero', '399.99', 1),
  product(2, 'LITERA-INF-01', 'Litera infantil', '399.99', 2),
  product(3, 'MESA-COMEDOR-01', 'Mesa de comedor', '249.50', 3),
  product(4, 'SILLA-NORDICA-01', 'Silla nórdica', '59.90', 4),
  product(5, 'LAMPARA-PIE-01', 'Lámpara de pie', '89.00', 5, null),
  product(6, 'SOFA-CHAISE-01', 'Sofá chaise longue', '749.00', 1),
  product(7, 'CAMA-MATRI-01', 'Cama de matrimonio', '529.00', 2),
  product(8, 'MESA-CENTRO-01', 'Mesa de centro', '129.99', 3),
  product(9, 'SILLA-OFICINA-01', 'Silla de oficina', '189.95', 4),
  product(10, 'LAMPARA-MESA-01', 'Lámpara de mesa', '39.95', 5),
  product(11, 'ESTANTERIA-MOD-01', 'Estantería modular', '219.00', 3, null),
  product(12, 'COLCHON-VISCO-01', 'Colchón viscoelástico', '349.00', 2),
  product(13, 'SOFA-CAMA-01', 'Sofá cama', '599.00', 1),
];

export const stores: Store[] = [
  {
    id: 1,
    name: 'Ukun Online',
    isOnline: true,
    phone: '+34900000001',
    address: { street: 'Calle Uría 1', city: 'Oviedo', province: 'Asturias', postalCode: '33003' },
  },
  {
    id: 2,
    name: 'Ukun Gijón',
    isOnline: false,
    phone: '+34900000002',
    address: { street: 'Avenida de la Costa 22', city: 'Gijón', province: 'Asturias', postalCode: '33205' },
  },
];

/**
 * Units per store. Product 4 is stocked but at zero, and product 11 has **no row at all** — both
 * come back as unavailable, never as unknown (RF-01).
 */
const stock: Record<number, Record<number, number>> = {
  1: { 1: 4, 2: 2, 3: 7, 4: 0, 5: 3, 6: 1, 7: 5, 8: 9, 9: 2, 10: 6, 12: 3, 13: 1 },
  2: { 1: 1, 3: 2, 9: 4 },
};

/** The mock's stand-in for the database's `unaccent(lower(...))` (defect #22). */
const normalise = (value: string) => value.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase();

const problem = (status: number, code: ProblemDetail['code'], extra: Partial<ProblemDetail> = {}) =>
  HttpResponse.json<ProblemDetail>(
    {
      type: `https://ukun.dev/errors/${code.toLowerCase().replaceAll('_', '-')}`,
      title: code,
      status,
      code,
      traceId: '00000000000000000000000000000000',
      ...extra,
    },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  );

export const catalogHandlers = [
  http.get(`${base}/api/products`, ({ request }) => {
    const params = new URL(request.url).searchParams;
    const name = params.get('name');
    const typeId = params.get('typeId');
    const priceMin = params.get('priceMin');
    const priceMax = params.get('priceMax');
    const descending = params.get('sort') === 'PRICE_DESC';
    const page = Number(params.get('page') ?? 0);
    const size = Number(params.get('size') ?? 20);

    const matched = products
      .filter((item) => (name ? normalise(item.name).includes(normalise(name)) : true))
      .filter((item) => (typeId ? item.typeId === Number(typeId) : true))
      .filter((item) => (priceMin ? Number(item.price) >= Number(priceMin) : true))
      .filter((item) => (priceMax ? Number(item.price) <= Number(priceMax) : true))
      // `id` as the last criterion, like the API: without it the two products at 399.99 can show up
      // on two pages or on none (defect #23).
      .sort((left, right) => {
        const byPrice = Number(left.price) - Number(right.price);
        return (descending ? -byPrice : byPrice) || left.id - right.id;
      });

    const body: ProductPage = {
      page,
      size,
      totalElements: matched.length,
      totalPages: Math.max(1, Math.ceil(matched.length / size)),
      items: matched.slice(page * size, page * size + size),
    };
    return HttpResponse.json(body);
  }),

  http.get(`${base}/api/product-types`, () => HttpResponse.json(productTypes)),

  http.get(`${base}/api/stores`, () => HttpResponse.json(stores)),

  http.get(`${base}/api/availability`, ({ request }) => {
    const params = new URL(request.url).searchParams;
    const storeId = Number(params.get('store'));
    const requested = (params.get('products') ?? '')
      .split(',')
      .filter((value) => value !== '')
      .map(Number);

    if (!stock[storeId]) {
      return problem(404, 'RESOURCE_NOT_FOUND', { resource: 'store', id: String(storeId) });
    }
    if (requested.length === 0) {
      return problem(400, 'VALIDATION_FAILED', {
        errors: [{ field: 'products', message: 'must not be empty' }],
      });
    }

    const body: AvailabilityResponse = {
      storeId,
      // One entry per requested identifier, always — a missing stock row is `false`, not a gap.
      items: requested.map((productId) => ({
        productId,
        available: (stock[storeId][productId] ?? 0) > 0,
      })),
    };
    return HttpResponse.json(body);
  }),
];
