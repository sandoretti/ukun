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

/**
 * «Sobre Ukun». It is `features/about`'s screen and it comes up here for the same reason the others
 * did: a second place writes it, and that place is `shared/ui/SiteHeader` — the bar cannot import a
 * feature, and until this route existed the entry was not a link at all.
 */
export const ABOUT_PATH = '/about';

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
 * The back-office, which is a **place** and not one screen: everything an employee does at work is
 * painted inside its own shell (`app/BackofficeLayout`), and this is the door to it. The session menu
 * of `features/auth` carries exactly one entry, and it points here whatever the roles are — what the
 * account may open is decided once, by the rail, from `GET /api/me`.
 *
 * The prefix is `/backoffice/*` and stays deliberately apart from `/admin/*`: ADR-0020 makes that
 * second one a biconditional with `/api/admin/**`, and both branches are painted inside this shell.
 */
export const BACKOFFICE_PATH = '/backoffice';

/**
 * The stock back-office (RF-09). It hangs off `/backoffice/*` and not `/admin/*` because managing
 * stock is any employee's job and talks to `/api/stores/**`.
 *
 * It comes up here because it is the first entry of the rail, which lives in `app/`. The rest of that
 * feature's paths stay in `features/backoffice/routes.ts`, because nobody else writes them.
 */
export const BACKOFFICE_STOCK_PATH = `${BACKOFFICE_PATH}/stock`;

/**
 * The branch of what talks to `/api/admin/**` (ADR-0020), which the stock screens deliberately do
 * not: the catalog (RF-10), the product types (RF-13) and the staff (RF-11) hang off it.
 *
 * **It is a prefix and no longer a screen.** It used to serve an index of cards that was the only way
 * to reach those screens; the rail of `BackofficeLayout` is that navigation now, so `/admin` on its
 * own redirects to `BACKOFFICE_PATH` and the two ways of saying the same thing became one. The paths
 * built on top of it stay in `features/backoffice/`, because nobody outside writes them.
 */
export const ADMIN_PATH = '/admin';
