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
