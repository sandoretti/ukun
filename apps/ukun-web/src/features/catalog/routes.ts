/**
 * The URLs of the catalog, in one place. They are written from four sides —the router, the card of
 * the listing, the breadcrumb and the type chip of the sheet— and a path spelled out at each of them
 * is a broken link waiting for the day one of them changes.
 *
 * The filter is a query parameter and not a segment because that is how the listing keeps its state
 * (`useCatalogParams`): `/products?typeId=1` is the catalogue itself, already filtered, and not a
 * different screen.
 */
export const CATALOG_PATH = '/products';

/** The router's pattern for the detail sheet — the only place the `:id` placeholder is written. */
export const PRODUCT_ROUTE = `${CATALOG_PATH}/:id`;

export const productPath = (id: number) => `${CATALOG_PATH}/${id}`;

export const catalogByTypePath = (typeId: number) => `${CATALOG_PATH}?typeId=${typeId}`;
