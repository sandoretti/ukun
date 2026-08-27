import { ADMIN_PATH } from '../../../shared/routes';

/**
 * The product types screen (RF-13), and the one place that knows its URL.
 *
 * It hangs off `/admin/*` because it talks to `/api/admin/product-types`, and ADR-0020 makes that
 * prefix a biconditional: managing the catalog's categories is an administrator's job, unlike the
 * stock, which is any employee's. The path stays in this feature, where `sections.ts` picks it up.
 *
 * There is no `search()` or `path(filters)` next to it, unlike the products and the staff: the
 * listing is a flat array with no filter, no sort and no page (`GET /api/admin/product-types`), so
 * there is no state to put in a query string and nothing to address but the screen itself.
 */
export const ADMIN_PRODUCT_TYPES_PATH = `${ADMIN_PATH}/product-types`;
