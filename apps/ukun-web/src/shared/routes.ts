/**
 * Routes that something outside their own feature has to link to.
 *
 * The criterion is the one the structure already imposes: a path used only by its feature and by the
 * router lives in that feature (`features/auth/routes.ts`, `features/catalog/routes.ts`), and one a
 * **second** feature needs comes up here, because features never import each other. It is the same
 * move `SIGN_IN_PATH` made into `shared/auth/signIn.ts`, for the same reason.
 *
 * The customer profile (RF-05) is the first of these: it is `features/profile`'s screen, and both the
 * account screen and the session menu of `features/auth` send the reader to it.
 */
export const PROFILE_PATH = '/profile';

/**
 * The cart (RF-03) is the second: it is `features/cart`'s screen, and the bar links to it from every
 * route while the product sheet of `features/catalog` sends the reader there after adding something.
 */
export const CART_PATH = '/cart';

/**
 * The catalogue and the sheet of one product. They belong to `features/catalog`, but the cart links
 * back to a product from every one of its lines — which is the whole reason a line carries a name —
 * and that is a second feature, so the path comes up here.
 *
 * Only the two the outside needs travel: `PRODUCT_ROUTE` (the router's `:id` pattern) and
 * `catalogByTypePath` stay in `features/catalog/routes.ts`, because nobody else writes them.
 */
export const CATALOG_PATH = '/products';

export const productPath = (id: number) => `${CATALOG_PATH}/${id}`;

/**
 * Checkout (RF-04) and the order history. They are `features/orders`' screens, and both are reached
 * from outside it: the cart's summary is where a purchase moves forward, and the account screen is
 * where a customer looks for what they have already bought.
 *
 * `orderPath` is up here too because the confirmation screen a fresh order lands on is the same
 * `/orders/{id}` the history links to — it is one screen with two ways in, not two.
 */
export const CHECKOUT_PATH = '/checkout';

export const ORDERS_PATH = '/orders';

export const orderPath = (id: number) => `${ORDERS_PATH}/${id}`;

/**
 * The stock back-office (RF-09). It is `features/backoffice`'s screen and it comes up here because
 * the session menu of `features/auth` is where an employee reaches it from.
 *
 * The rest of that feature's paths stay in `features/backoffice/routes.ts`, because nobody else
 * writes them. And the branch is `/backoffice/*`, never `/admin/*`: that prefix belongs to what talks
 * to `/api/admin/**` (ADR-0020), and stock is any employee's, not an administrator's.
 */
export const BACKOFFICE_STOCK_PATH = '/backoffice/stock';
