import type { Role } from '../../shared/api/session';
import { BACKOFFICE_STOCK_PATH } from '../../shared/routes';
import { ADMIN_PRODUCTS_PATH } from './adminRoutes';
import { ADMIN_EMPLOYEES_PATH } from './employees/routes';

export type BackofficeSection = {
  /** Identifies the entry — in a `key` prop, and in a test. */
  key: string;
  to: string;
  /** Material Symbols name, drawn by `shared/ui/Icon`. */
  icon: string;
  /** What the account has to hold for this section to be offered. */
  role: Role;
  /**
   * The two captions, **written out** rather than composed into `sections.${key}.title` where they
   * are read. A key built at runtime is a key the parity test of `i18n/i18n.test.ts` cannot see, so a
   * missing translation would only ever show up on screen — the same reason `StockFilters` writes its
   * three view labels out one by one.
   */
  titleKey: string;
  bodyKey: string;
};

/**
 * What the back-office contains, in order, each with the role it asks for.
 *
 * **It is one list read from two places** — the rail of `app/BackofficeRail` and the index at
 * `/backoffice` — because they are two views of the same question and were drifting apart as two:
 * the old `/admin` index knew about the catalogue and the staff but not about the stock, and nothing
 * knew about all three. Adding a screen is now one entry here, one pair of captions in the two
 * `backoffice.json`, and a route.
 *
 * The roles are the reason it is data and not markup. `EMPLOYEE` and `ADMIN` are not two lists but
 * one list read through `useAuth().hasRole` — hiding what an account cannot open is UX, never the
 * barrier: the gateway refuses `/api/admin/**` without `ADMIN`, `ukun-api` checks it again, and
 * `RequireRole` turns the route away (`docs/08-identidad-y-seguridad.md` §4).
 *
 * **It lists only what exists.** Product types (RF-13) will add their entry when they have a screen
 * to point at; announcing them first would be a menu that lies about what the back-office can do.
 */
export const BACKOFFICE_SECTIONS = [
  {
    key: 'stock',
    to: BACKOFFICE_STOCK_PATH,
    icon: 'inventory',
    role: 'EMPLOYEE',
    titleKey: 'sections.stock.title',
    bodyKey: 'sections.stock.body',
  },
  {
    key: 'products',
    to: ADMIN_PRODUCTS_PATH,
    icon: 'inventory_2',
    role: 'ADMIN',
    titleKey: 'sections.products.title',
    bodyKey: 'sections.products.body',
  },
  {
    key: 'employees',
    to: ADMIN_EMPLOYEES_PATH,
    icon: 'badge',
    role: 'ADMIN',
    titleKey: 'sections.employees.title',
    bodyKey: 'sections.employees.body',
  },
] as const satisfies readonly BackofficeSection[];
