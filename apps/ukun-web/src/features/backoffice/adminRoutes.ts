import { ADMIN_PATH } from '../../shared/routes';
import type { AdminProductFilterInput, AdminProductSort, ProductStatusFilter } from './api/adminProductKeys';
import { FIRST_PAGE } from './routes';

/**
 * The paths of the admin half of this feature, and the one place that knows what a back-office
 * catalog URL looks like.
 *
 * The whole branch hangs off `/admin/*` and not `/backoffice/*`: that prefix is what talks to
 * `/api/admin/**` and is an administrator's alone (ADR-0020, `docs/07-frontend-ux-ui.md`), while
 * managing stock is any employee's job. `ADMIN_PATH` itself is not declared here — it is a prefix
 * shared with the staff screens, and a path a second module needs goes up to `shared/routes.ts`.
 *
 * It is a file of its own rather than more lines in `routes.ts` so that the two halves of the
 * back-office —stock (RF-09) and catalog (RF-10)— can be worked on without touching each other.
 */
export const ADMIN_PRODUCTS_PATH = `${ADMIN_PATH}/products`;

export const NEW_PRODUCT_PATH = `${ADMIN_PRODUCTS_PATH}/new`;

/** The router's pattern. It goes **after** the literal `/new` in `App.tsx`, which `:id` also matches. */
export const ADMIN_PRODUCT_ROUTE = `${ADMIN_PRODUCTS_PATH}/:id`;

/**
 * The sheet of one product, addressed by its internal identifier and never by its SKU.
 *
 * There is no contradiction with ADR-0015: the SKU is the business key the admin *searches* by
 * (`?sku=`), and it is immutable precisely because it is not the address. The contract makes the same
 * distinction in the description of `ProductIdParam`.
 */
export const adminProductPath = (id: number) => `${ADMIN_PRODUCTS_PATH}/${id}`;

/** What the listing opens on, which is also the contract's own default for each parameter. */
export const DEFAULT_STATUS: ProductStatusFilter = 'ACTIVE';
export const DEFAULT_SORT: AdminProductSort = 'NAME_ASC';

/**
 * The listing's query string. Defaults are left out: `/admin/products` and
 * `?status=ACTIVE&sort=NAME_ASC&page=1` are the same screen, and only one of the two is worth putting
 * in someone's address bar.
 */
export function adminProductsSearch(
  filters: Partial<AdminProductFilterInput>,
  page: number = FIRST_PAGE,
): URLSearchParams {
  const params = new URLSearchParams();

  if (filters.q) {
    params.set('q', filters.q);
  }
  if (filters.sku) {
    params.set('sku', filters.sku);
  }
  if (filters.typeId !== undefined && filters.typeId !== null) {
    params.set('typeId', String(filters.typeId));
  }
  if (filters.status !== undefined && filters.status !== DEFAULT_STATUS) {
    params.set('status', filters.status);
  }
  if (filters.sort !== undefined && filters.sort !== DEFAULT_SORT) {
    params.set('sort', filters.sort);
  }
  if (page > FIRST_PAGE) {
    params.set('page', String(page));
  }

  return params;
}

/**
 * The listing, filtered. It is how a screen sends the reader to one specific row without inventing a
 * way to address one: filtering by SKU leaves exactly the product in question on screen, which is
 * what a fresh creation and a "this SKU already exists" both need to land on.
 */
export function adminProductsPath(
  filters: Partial<AdminProductFilterInput> = {},
  page: number = FIRST_PAGE,
): string {
  const query = adminProductsSearch(filters, page).toString();

  return query === '' ? ADMIN_PRODUCTS_PATH : `${ADMIN_PRODUCTS_PATH}?${query}`;
}
