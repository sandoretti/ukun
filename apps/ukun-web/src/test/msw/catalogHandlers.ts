import { http, HttpResponse } from 'msw';

import type { components } from '../../types/api';

/**
 * Test-only stand-in for the catalog endpoints of `ukun-api`, typed from the same generated OpenAPI
 * types the app uses, so a contract change breaks compilation here too. It is not a dev backend:
 * `npm run dev` talks to the real service through the Vite proxy.
 */

type ProductSummary = components['schemas']['ProductSummary'];
type ProductDetail = components['schemas']['ProductDetail'];
type ProductType = components['schemas']['ProductType'];
type ProductPage = components['schemas']['ProductPage'];
type ProductSuggestion = components['schemas']['ProductSuggestion'];
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
const curated: ProductSummary[] = [
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

/** Filler catalogue: names, so that a generated product still reads like one on screen. */
const FILLER_NAMES = [
  ['Butaca', 'BUTACA'],
  ['Cómoda', 'COMODA'],
  ['Escritorio', 'ESCRITORIO'],
  ['Aparador', 'APARADOR'],
  ['Taburete', 'TABURETE'],
  ['Perchero', 'PERCHERO'],
  ['Alfombra', 'ALFOMBRA'],
  ['Cabecero', 'CABECERO'],
  ['Baúl', 'BAUL'],
  ['Vitrina', 'VITRINA'],
  ['Biombo', 'BIOMBO'],
  ['Zapatero', 'ZAPATERO'],
];

const FILLER_COUNT = 92;

/**
 * A generated tail on top of the thirteen curated products. A listing that caps a page at 72 items
 * cannot be seen — nor tested — with thirteen: it takes more than one page to tell "there is no
 * more" from "there is more on the next page".
 *
 * The filler is deliberately kept out of the way of what the curated rows are there to prove: no
 * name of it matches the terms the search tests type, none of it is a sofa (`typeId` 1), and every
 * price sits strictly between 100 and 500, so the counts the price filters assert do not move.
 * Prices grow with the identifier, which makes the order by price predictable all the way down.
 */
const filler = (index: number): ProductSummary => {
  const id = curated.length + index + 1;
  const [name, slug] = FILLER_NAMES[index % FILLER_NAMES.length];
  const number = String(index + 1).padStart(2, '0');
  return product(id, `${slug}-${number}`, `${name} ${number}`, (110 + index * 4).toFixed(2), 2 + (index % 4));
};

export const products: ProductSummary[] = [
  ...curated,
  ...Array.from({ length: FILLER_COUNT }, (_, index) => filler(index)),
];

/**
 * The one field the detail carries beyond the summary. Only a few products have one, and the rest
 * come back with `description: null` — the contract allows it (RF-10 uploads the text later) and the
 * sheet has to say so instead of leaving a gap. Two lines in one of them, because the description is
 * free text and its line breaks are content.
 */
const descriptions: Record<number, string> = {
  1: 'Sofá de tres plazas tapizado en cuero curtido al vegetal.\nEstructura de madera maciza.',
  4: 'Silla de comedor de haya con asiento moldeado.',
  6: 'Chaise longue reversible: el brazo se monta a izquierda o a derecha.',
};


/**
 * Units per store, keyed by the identifiers of `storeHandlers` (1 is the online one). Product 4 is
 * stocked but at zero, and product 11 has **no row at all** — both come back as unavailable, never
 * as unknown (RF-01).
 */
const stock: Record<number, Record<number, number>> = {
  1: {
    1: 4, 2: 2, 3: 7, 4: 0, 5: 3, 6: 1, 7: 5, 8: 9, 9: 2, 10: 6, 12: 3, 13: 1,
    // The generated tail is stocked too — a grid where every card past the thirteenth says "out of
    // stock" is not what scrolling through a catalogue looks like. One in seven of it sits at zero
    // and one in eleven has no row at all, so both ways of being unavailable turn up early enough
    // to be seen without paging to the end.
    ...Object.fromEntries(
      Array.from({ length: FILLER_COUNT }, (_, index) => curated.length + index + 1)
        .filter((id) => id % 11 !== 0)
        .map((id) => [id, id % 7 === 0 ? 0 : (id % 5) + 1]),
    ),
  },
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

    // The same ceiling `PaginationParams` enforces: without it a listing is an unbounded `findAll()`
    // again, and a frontend asking for a page of ten thousand would only find out in production.
    if (size > 100) {
      return problem(400, 'VALIDATION_FAILED', {
        errors: [{ field: 'size', message: 'must be less than or equal to 100' }],
      });
    }

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

  /**
   * Typeahead of the search box. A flat array with no pagination wrapper, like the contract: not
   * carrying `totalElements` is what saves the `COUNT` on every keystroke.
   */
  http.get(`${base}/api/products/suggest`, ({ request }) => {
    const params = new URL(request.url).searchParams;
    const term = params.get('q') ?? '';
    const limit = Number(params.get('limit') ?? 8);

    if (term.trim() === '') {
      return problem(400, 'VALIDATION_FAILED', {
        errors: [{ field: 'q', message: 'must not be blank' }],
      });
    }

    const body: ProductSuggestion[] = products
      .filter((item) => normalise(item.name).includes(normalise(term)))
      .sort((left, right) => left.name.localeCompare(right.name))
      .slice(0, limit)
      .map(({ id, sku, name, price, imageUrl }) => ({ id, sku, name, price, imageUrl }));

    return HttpResponse.json(body);
  }),

  /**
   * The detail sheet. It goes **after** the typeahead on purpose: MSW resolves in registration
   * order and `:id` also matches `/api/products/suggest`.
   *
   * The public catalog only resolves active products, so anything that is not in the list answers
   * `404` with `code: RESOURCE_NOT_FOUND` — the same reply a discontinued product gets (ADR-0013).
   */
  http.get(`${base}/api/products/:id`, ({ params }) => {
    const found = products.find((item) => item.id === Number(params.id));

    if (!found) {
      return problem(404, 'RESOURCE_NOT_FOUND', { resource: 'product', id: String(params.id) });
    }

    const body: ProductDetail = { ...found, description: descriptions[found.id] ?? null };
    return HttpResponse.json(body);
  }),

  http.get(`${base}/api/product-types`, () => HttpResponse.json(productTypes)),

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
