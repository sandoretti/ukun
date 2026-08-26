import { http, HttpResponse } from 'msw';

import type { components } from '../../types/api';
import { normalise, productTypes } from './catalogHandlers';
import { problem } from './problem';

/**
 * Test-only stand-in for the back-office catalog of `ukun-api` (RF-10), typed from the same generated
 * OpenAPI types the app uses, so a contract change breaks compilation here too.
 *
 * Like the cart's and the stock's, this double **keeps state**: the point of these screens is that a
 * write changes what the next read answers, and a stateless stub would let a test pass while the
 * cache was never refreshed. The state belongs to each `adminProductState(...)` call, so it dies with
 * the test that installed it.
 *
 * It is a separate file from `catalogHandlers` on purpose: what is mocked here is a different
 * endpoint with a different contract — it resolves discontinued products, it is the only multipart
 * operation of the service, and it can refuse a deletion.
 */

type AdminProduct = components['schemas']['AdminProduct'];
type AdminProductPage = components['schemas']['AdminProductPage'];
type CreateProductRequest = components['schemas']['CreateProductRequest'];
type UpdateProductRequest = components['schemas']['UpdateProductRequest'];
type AdminProductSort = components['schemas']['AdminProductSort'];
type ErrorCode = components['schemas']['ErrorCode'];

const base = import.meta.env.VITE_API_URL || '';

/**
 * A product as the database would hold it, plus how many things point at it — which is what decides
 * whether `DELETE` answers `204` or `409 PRODUCT_IN_USE`.
 */
export type SeedProduct = AdminProduct & {
  references: { inOrders: number; inStock: number; inCarts: number };
};

const NO_REFERENCES = { inOrders: 0, inStock: 0, inCarts: 0 };

let nextId = 1;

/**
 * One product, with everything a test does not care about already filled in. Names and types come
 * from the same seed the public catalog uses, so the two doubles describe the same shop.
 */
export function adminProduct(overrides: Partial<SeedProduct> = {}): SeedProduct {
  const id = overrides.id ?? nextId++;

  return {
    id,
    sku: `SKU-${String(id).padStart(3, '0')}`,
    name: `Producto ${id}`,
    description: 'Descripción del producto.',
    price: '99.00',
    typeId: productTypes[0].id,
    typeName: productTypes[0].name,
    imageUrl: null,
    isActive: true,
    createdAt: '2026-01-01T10:00:00Z',
    updatedAt: '2026-01-01T10:00:00Z',
    references: NO_REFERENCES,
    ...overrides,
  };
}

const typeNameOf = (typeId: number) => productTypes.find((type) => type.id === typeId)?.name;

function sorted(items: SeedProduct[], sort: AdminProductSort): SeedProduct[] {
  const byName = (left: SeedProduct, right: SeedProduct) => left.name.localeCompare(right.name);
  const byPrice = (left: SeedProduct, right: SeedProduct) => Number(left.price) - Number(right.price);
  const byUpdated = (left: SeedProduct, right: SeedProduct) =>
    Date.parse(right.updatedAt) - Date.parse(left.updatedAt);

  const criterion = {
    NAME_ASC: byName,
    NAME_DESC: (left: SeedProduct, right: SeedProduct) => -byName(left, right),
    PRICE_ASC: byPrice,
    PRICE_DESC: (left: SeedProduct, right: SeedProduct) => -byPrice(left, right),
    UPDATED_DESC: byUpdated,
  }[sort];

  // `id` as the last criterion, like the API: without it a tie can show up on two pages or on none
  // (defect #23).
  return [...items].sort((left, right) => criterion(left, right) || left.id - right.id);
}

/** What the app sees. The reference counts are the database's business, never the response's. */
function toResponse(seed: SeedProduct): AdminProduct {
  return {
    id: seed.id,
    sku: seed.sku,
    name: seed.name,
    description: seed.description,
    price: seed.price,
    typeId: seed.typeId,
    typeName: seed.typeName,
    imageUrl: seed.imageUrl,
    isActive: seed.isActive,
    createdAt: seed.createdAt,
    updatedAt: seed.updatedAt,
  };
}

/**
 * The back-office catalog with a life of its own for one test:
 * `server.use(...adminProductState({ products: [adminProduct()] }).handlers)`.
 *
 * It reproduces the contract: the listing filters, sorts and pages; the `POST` refuses a duplicate
 * SKU (`409 DUPLICATE_VALUE`) and an unknown type (`404`); the `PUT` edits everything except the SKU
 * and the status; `:discontinue` and `:reactivate` are idempotent; the `DELETE` refuses a referenced
 * product with `409 PRODUCT_IN_USE` and its three extensions; and the image sub-resource answers the
 * `ProductImage` of the upload and a `204` for the removal.
 */
export function adminProductState(options: { products?: readonly SeedProduct[] } = {}) {
  let rows: SeedProduct[] = (options.products ?? []).map((product) => ({ ...product }));
  let created = 0;

  /** Every listing query string, in order: what proves a filter was applied by the server, not here. */
  const searches: string[] = [];
  /** Every write body, so a test can assert what travelled instead of what was typed. */
  const bodies: unknown[] = [];
  /** Every upload's declared type: what proves the multipart carried a part named `file`, with it. */
  const uploads: { type: string }[] = [];

  const find = (id: number) => rows.find((row) => row.id === id);

  const replace = (updated: SeedProduct) => {
    rows = rows.map((row) => (row.id === updated.id ? updated : row));
    return updated;
  };

  const handlers = [
    http.get(`${base}/api/admin/products`, ({ request }) => {
      const search = new URL(request.url).searchParams;
      searches.push(search.toString());

      const q = search.get('q');
      const sku = search.get('sku');
      const typeId = search.get('typeId');
      const status = search.get('status') ?? 'ACTIVE';
      const sort = (search.get('sort') ?? 'NAME_ASC') as AdminProductSort;
      const page = Number(search.get('page') ?? 0);
      const size = Number(search.get('size') ?? 20);

      const matched = sorted(
        rows
          .filter((row) => (q ? normalise(row.name).includes(normalise(q)) : true))
          .filter((row) => (sku ? row.sku === sku : true))
          .filter((row) => (typeId ? row.typeId === Number(typeId) : true))
          .filter((row) => {
            if (status === 'ALL') {
              return true;
            }
            return status === 'ACTIVE' ? row.isActive : !row.isActive;
          }),
        sort,
      );

      const body: AdminProductPage = {
        page,
        size,
        totalElements: matched.length,
        totalPages: Math.max(1, Math.ceil(matched.length / size)),
        items: matched.slice(page * size, page * size + size).map(toResponse),
      };

      return HttpResponse.json(body);
    }),

    http.post(`${base}/api/admin/products`, async ({ request }) => {
      const body = (await request.json()) as CreateProductRequest;
      bodies.push(body);

      if (rows.some((row) => row.sku === body.sku)) {
        // `ux_product_sku`: a SKU belongs to one product for life, discontinued or not.
        return problem('DUPLICATE_VALUE', 409, { resource: 'product', field: 'sku' });
      }

      const name = typeNameOf(body.typeId);

      if (name === undefined) {
        return problem('RESOURCE_NOT_FOUND', 404, { resource: 'productType', id: String(body.typeId) });
      }

      const product = adminProduct({
        sku: body.sku,
        name: body.name,
        description: body.description,
        price: body.price,
        typeId: body.typeId,
        typeName: name,
        references: NO_REFERENCES,
      });

      rows = [...rows, product];
      created += 1;

      return HttpResponse.json(toResponse(product), { status: 201 });
    }),

    /**
     * The two catalog decisions. They share a handler because they share a path segment: the contract
     * spells them `{id}:discontinue` and `{id}:reactivate`, so what arrives is one segment carrying
     * both the identifier and the verb.
     */
    http.post(`${base}/api/admin/products/:target`, ({ params }) => {
      const [id, operation] = String(params.target).split(':');
      const product = find(Number(id));

      if (product === undefined) {
        return problem('RESOURCE_NOT_FOUND', 404, { resource: 'product', id });
      }

      // Idempotent: asking for what it already is answers the same product.
      return HttpResponse.json(
        toResponse(replace({ ...product, isActive: operation === 'reactivate' })),
      );
    }),

    http.get(`${base}/api/admin/products/:id`, ({ params }) => {
      const product = find(Number(params.id));

      return product === undefined
        ? problem('RESOURCE_NOT_FOUND', 404, { resource: 'product', id: String(params.id) })
        : HttpResponse.json(toResponse(product));
    }),

    http.put(`${base}/api/admin/products/:id/image`, async ({ request, params }) => {
      const product = find(Number(params.id));

      if (product === undefined) {
        return problem('RESOURCE_NOT_FOUND', 404, { resource: 'product', id: String(params.id) });
      }

      // The multipart is read as text and not with `request.formData()`. The file the browser hands
      // over is a jsdom `File`, and the parser behind `formData()` is Node's own, which refuses
      // anything that is not a `File` of its realm — a mismatch of the test environment, not of the
      // contract. Reading the part's headers proves what this doubles for: that the upload travelled
      // as one multipart part named `file`, carrying that file.
      const part = /name="(?<field>[^"]+)"; filename="(?<filename>[^"]*)"\r\nContent-Type: (?<type>[^\r]+)/u.exec(
        await request.text(),
      );

      if (part?.groups === undefined || part.groups.field !== 'file') {
        return problem('VALIDATION_FAILED', 400, { errors: [{ field: 'file', message: 'required' }] });
      }

      // Only the declared type is recorded, not the file name: the browser under jsdom serialises the
      // part as an anonymous blob, and the name is the server's to generate anyway
      // (`docs/08-identidad-y-seguridad.md` §9).
      uploads.push({ type: part.groups.type });

      const imageUrl = `/media/products/${product.id}.webp`;
      replace({ ...product, imageUrl });

      return HttpResponse.json({ imageUrl });
    }),

    http.delete(`${base}/api/admin/products/:id/image`, ({ params }) => {
      const product = find(Number(params.id));

      if (product === undefined) {
        return problem('RESOURCE_NOT_FOUND', 404, { resource: 'product', id: String(params.id) });
      }

      replace({ ...product, imageUrl: null });

      return new HttpResponse(null, { status: 204 });
    }),

    http.put(`${base}/api/admin/products/:id`, async ({ request, params }) => {
      const product = find(Number(params.id));
      const body = (await request.json()) as UpdateProductRequest;
      bodies.push(body);

      if (product === undefined) {
        return problem('RESOURCE_NOT_FOUND', 404, { resource: 'product', id: String(params.id) });
      }

      const name = typeNameOf(body.typeId);

      if (name === undefined) {
        return problem('RESOURCE_NOT_FOUND', 404, { resource: 'productType', id: String(body.typeId) });
      }

      return HttpResponse.json(
        toResponse(replace({ ...product, ...body, typeName: name, updatedAt: '2026-02-01T10:00:00Z' })),
      );
    }),

    http.delete(`${base}/api/admin/products/:id`, ({ params }) => {
      const product = find(Number(params.id));

      if (product === undefined) {
        return problem('RESOURCE_NOT_FOUND', 404, { resource: 'product', id: String(params.id) });
      }

      const { inOrders, inStock, inCarts } = product.references;

      if (inOrders + inStock + inCarts > 0) {
        return problem('PRODUCT_IN_USE', 409, { inOrders, inStock, inCarts });
      }

      rows = rows.filter((row) => row.id !== product.id);

      return new HttpResponse(null, { status: 204 });
    }),
  ];

  return {
    handlers,
    searches,
    bodies,
    uploads,
    get created() {
      return created;
    },
    /** The catalog as it stands right now — what a write actually left behind. */
    get rows() {
      return rows;
    },
  };
}

/**
 * The default: an empty catalog and **no writes**. A test that writes installs its own state with
 * `adminProductState(...)`; without it the write has no handler and the run fails, which is the answer
 * this suite wants for a request nobody accounted for.
 */
export const adminProductHandlers = adminProductState().handlers.slice(0, 1);

/** The listing failing, by `code` — the screen has nothing to show and paints the error state. */
export function adminProductsFail(code: ErrorCode, status: number) {
  return http.get(`${base}/api/admin/products`, () => problem(code, status));
}

/** The sheet failing, for the screen that reads one product. */
export function adminProductFails(code: ErrorCode, status: number) {
  return http.get(`${base}/api/admin/products/:id`, () => problem(code, status));
}

/** The creation failing, for the outcomes the add screen has to tell apart. */
export function createProductFails(code: ErrorCode, status: number, extensions: Record<string, unknown> = {}) {
  return http.post(`${base}/api/admin/products`, () => problem(code, status, extensions));
}

/** The upload failing — the `413` and the `415` the server decides on the real content of the file. */
export function uploadImageFails(code: ErrorCode, status: number) {
  return http.put(`${base}/api/admin/products/:id/image`, () => problem(code, status));
}
