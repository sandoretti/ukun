import { http, HttpResponse } from 'msw';

import type { components } from '../../types/api';
import { problem } from './problem';

/**
 * Test-only stand-in for the product type endpoints of `ukun-api` (RF-13), typed from the same
 * generated OpenAPI types the app uses.
 *
 * Like the staff's, this double **keeps state**: the point of creating, renaming and deleting a type
 * is that the next listing answers differently, and a stateless stub would let a test pass while the
 * cache was never invalidated.
 *
 * It reproduces the three rules the screen is built on: the list comes back sorted by name, a name
 * already taken is `409 DUPLICATE_VALUE` naming the field that clashed, and a type with products is
 * `409 PRODUCT_TYPE_IN_USE` carrying how many.
 */

type AdminProductType = components['schemas']['AdminProductType'];
type ProductTypeRequest = components['schemas']['ProductTypeRequest'];
type ErrorCode = components['schemas']['ErrorCode'];

const base = import.meta.env.VITE_API_URL || '';

const ENDPOINT = `${base}/api/admin/product-types`;

/** A row as the database would hold it; the timestamps are noise for these screens. */
export type SeedProductType = Pick<AdminProductType, 'id' | 'name' | 'productCount'>;

export function productType(seed: SeedProductType): AdminProductType {
  return { ...seed, createdAt: '2026-01-15T10:00:00Z', updatedAt: '2026-01-15T10:00:00Z' };
}

/** Two types the catalog really has, one of them free of products and therefore deletable. */
export const seededTypes: SeedProductType[] = [
  { id: 1, name: 'Sofás', productCount: 12 },
  { id: 2, name: 'Iluminación', productCount: 0 },
];

/**
 * The catalog's types with a life of their own for one test:
 * `server.use(...productTypeState({ types: seededTypes }).handlers)`.
 *
 * `deletionAlwaysRejected` reproduces the race the screen has to survive: a type the listing showed
 * with no products that the server refuses anyway, because somebody gave a product that type in
 * between.
 */
export function productTypeState(
  options: { types?: readonly SeedProductType[]; deletionAlwaysRejected?: number } = {},
) {
  let rows: AdminProductType[] = (options.types ?? []).map(productType);
  let nextId = Math.max(0, ...rows.map((row) => row.id)) + 1;

  /** Every body a write was sent, so a test can check what travelled without reading the screen. */
  const bodies: ProductTypeRequest[] = [];
  const counters = { created: 0, renamed: 0, deleted: 0 };

  const duplicate = () => problem('DUPLICATE_VALUE', 409, { resource: 'productType', field: 'name' });

  const handlers = [
    http.get(ENDPOINT, () =>
      HttpResponse.json([...rows].sort((left, right) => left.name.localeCompare(right.name))),
    ),

    http.post(ENDPOINT, async ({ request }) => {
      const body = (await request.json()) as ProductTypeRequest;
      bodies.push(body);

      if (rows.some((row) => row.name === body.name)) {
        return duplicate();
      }

      // Born with no products, by definition.
      const created = productType({ id: nextId, name: body.name, productCount: 0 });
      nextId += 1;
      rows = [...rows, created];
      counters.created += 1;

      return HttpResponse.json(created, {
        status: 201,
        headers: { Location: `/api/admin/product-types/${created.id}` },
      });
    }),

    http.put(`${ENDPOINT}/:id`, async ({ params, request }) => {
      const id = Number(params.id);
      const body = (await request.json()) as ProductTypeRequest;
      bodies.push(body);

      const existing = rows.find((row) => row.id === id);
      if (existing === undefined) {
        return problem('RESOURCE_NOT_FOUND', 404, { resource: 'productType' });
      }
      if (rows.some((row) => row.id !== id && row.name === body.name)) {
        return duplicate();
      }

      const renamed: AdminProductType = { ...existing, name: body.name };
      rows = rows.map((row) => (row.id === id ? renamed : row));
      counters.renamed += 1;

      return HttpResponse.json(renamed);
    }),

    http.delete(`${ENDPOINT}/:id`, ({ params }) => {
      const id = Number(params.id);

      const existing = rows.find((row) => row.id === id);
      if (existing === undefined) {
        return problem('RESOURCE_NOT_FOUND', 404, { resource: 'productType' });
      }

      const inProducts = options.deletionAlwaysRejected ?? existing.productCount;
      if (inProducts > 0) {
        return problem('PRODUCT_TYPE_IN_USE', 409, { inProducts });
      }

      rows = rows.filter((row) => row.id !== id);
      counters.deleted += 1;

      return new HttpResponse(null, { status: 204 });
    }),
  ];

  return { handlers, bodies, counters };
}

/** The listing failing, by `code` — the screen has nothing to show and paints the error state. */
export function productTypesFail(code: ErrorCode, status: number) {
  return http.get(ENDPOINT, () => problem(code, status));
}
