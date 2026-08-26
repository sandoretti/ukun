import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { http } from '../../../shared/api/http';
import { retryUnlessClientError } from '../../../shared/api/retry';
import {
  adminProductKeys,
  type AdminProduct,
  type AdminProductFilters,
  type AdminProductPage,
  type CreateProductRequest,
  type ProductImage,
  type UpdateProductRequest,
} from './adminProductKeys';

/** What travels to the API. Empty criteria are left out rather than sent as blanks. */
function toQueryParams(filters: AdminProductFilters) {
  return {
    ...(filters.q ? { q: filters.q } : {}),
    ...(filters.sku ? { sku: filters.sku } : {}),
    ...(filters.typeId !== null ? { typeId: filters.typeId } : {}),
    status: filters.status,
    sort: filters.sort,
    page: filters.page,
    size: filters.size,
  };
}

/**
 * The back-office catalog, page by page (RF-10). The page number that arrives here already counts
 * from zero: the URL counts from one and the translation happens in `toApiFilters`, once.
 *
 * `keepPreviousData` keeps the table on screen while the next page loads instead of collapsing it on
 * every page change (`docs/07-frontend-ux-ui.md`).
 */
export function useAdminProducts(filters: AdminProductFilters) {
  // The generics are spelled out because `keepPreviousData` is generic itself: left to inference it
  // widens `data` into a union with the placeholder function, and every reader of the page would have
  // to narrow something that is never a function.
  return useQuery<AdminProductPage, Error>({
    queryKey: adminProductKeys.list(filters),
    queryFn: ({ signal }) =>
      http
        .get<AdminProductPage>('/api/admin/products', { params: toQueryParams(filters), signal })
        .then((response) => response.data),
    placeholderData: keepPreviousData,
    retry: retryUnlessClientError,
  });
}

/**
 * One product as the back-office sees it, **discontinued ones included** — which is the whole
 * difference with the public `GET /api/products/{id}` and the reason the sheet can offer to
 * reactivate what it is showing (ADR-0013).
 */
export function useAdminProduct(id: number) {
  return useQuery({
    queryKey: adminProductKeys.detail(id),
    queryFn: ({ signal }) =>
      http.get<AdminProduct>(`/api/admin/products/${id}`, { signal }).then((response) => response.data),
    retry: retryUnlessClientError,
  });
}

/**
 * What every write to the catalog has to refresh, and what it deliberately does not.
 *
 * Every listing and every sheet, by prefix: a rename changes a row of the table and the sheet behind
 * it, and a discontinuation moves a product in or out of whatever `status` is being looked at.
 *
 * **The public catalog is not invalidated from here.** Its key factory lives in `features/catalog`
 * and features never import each other (`docs/07-frontend-ux-ui.md`); writing `['products', ...]` by
 * hand to reach it would be the same key in two places, which is the cache bug the conventions warn
 * about. It costs nothing: those reads carry no `staleTime`, so the first time an admin walks into
 * the shop's own catalog it is read again anyway.
 */
function useInvalidateCatalog() {
  const queryClient = useQueryClient();

  return () => queryClient.invalidateQueries({ queryKey: adminProductKeys.all });
}

/**
 * Writing the server's own answer into the sheet's cache entry.
 *
 * The three operations that answer with an `AdminProduct` —the edit and the two catalog decisions—
 * hand back the row exactly as it now is, so the sheet has no reason to blink through a re-read to
 * show what it already has. The invalidation still runs behind it: the listings need it, and this
 * entry gets checked against the server like any other.
 */
function useStoreProduct() {
  const queryClient = useQueryClient();

  return (product: AdminProduct) => queryClient.setQueryData(adminProductKeys.detail(product.id), product);
}

/**
 * Creating a product (RF-10). It is born **active and without a photo**: the request carries neither,
 * and the photo is uploaded afterwards against its own sub-resource.
 *
 * Two failures the screen has to tell apart, and this hook only reports: `409 DUPLICATE_VALUE` means
 * that SKU already belongs to another product —for life, discontinued or not (ADR-0015)— and
 * `404 RESOURCE_NOT_FOUND` means the type does not exist.
 */
export function useCreateProduct() {
  const invalidate = useInvalidateCatalog();

  return useMutation({
    mutationFn: (request: CreateProductRequest) =>
      http.post<AdminProduct>('/api/admin/products', request).then((response) => response.data),
    onSuccess: invalidate,
  });
}

export type UpdateProductVariables = UpdateProductRequest & { id: number };

/**
 * Editing a product: name, description, price and type, and nothing else.
 *
 * **No `sku`** (immutable, ADR-0015), **no `isActive`** (that is `:discontinue`/`:reactivate`, an
 * audited catalog decision that has no business being buried inside a price change) and **no image**
 * (its own sub-resource).
 *
 * There is no `version` and no optimistic locking here, and that is deliberate rather than forgotten:
 * editing a product is last-write-wins by explicit decision (`docs/10-modelo-de-datos.md` §2). Nothing
 * in this screen shows a `VersionConflictPanel`, which belongs to `store_stock` and to ADR-0008.
 */
export function useUpdateProduct() {
  const invalidate = useInvalidateCatalog();
  const store = useStoreProduct();

  return useMutation({
    mutationFn: ({ id, ...request }: UpdateProductVariables) =>
      http.put<AdminProduct>(`/api/admin/products/${id}`, request).then((response) => response.data),
    onSuccess: async (product) => {
      store(product);
      await invalidate();
    },
  });
}

/**
 * Taking a product out of the catalog, and putting it back (RF-10, ADR-0013).
 *
 * **This is not deleting it**: the product keeps existing, its order history is untouched and the
 * decision is reversible — which is exactly why it is the normal operational way out and deletion is
 * not. Both operations are idempotent, so pressing twice is not an error, and both answer with the
 * product as it now stands.
 */
function useCatalogDecision(operation: 'discontinue' | 'reactivate') {
  const invalidate = useInvalidateCatalog();
  const store = useStoreProduct();

  return useMutation({
    mutationFn: (id: number) =>
      http
        .post<AdminProduct>(`/api/admin/products/${id}:${operation}`)
        .then((response) => response.data),
    onSuccess: async (product) => {
      store(product);
      await invalidate();
    },
  });
}

export function useDiscontinueProduct() {
  return useCatalogDecision('discontinue');
}

export function useReactivateProduct() {
  return useCatalogDecision('reactivate');
}

/**
 * Deleting a product for good, image included.
 *
 * It only succeeds for a product that has **never** been referenced. A `409 PRODUCT_IN_USE` carries
 * `inOrders`, `inStock` and `inCarts` as extensions, and the dialog paints where those references are
 * instead of a generic refusal — the answer to it is to discontinue, not to try again.
 *
 * `204` and no body, so there is nothing to write into the cache: the new state comes from the
 * re-read the invalidation triggers.
 */
export function useDeleteProduct() {
  const invalidate = useInvalidateCatalog();

  return useMutation({
    mutationFn: async (id: number) => {
      await http.delete(`/api/admin/products/${id}`);
    },
    onSuccess: invalidate,
  });
}

export type UploadProductImageVariables = { id: number; file: File };

/**
 * Uploading the photo — **the only multipart operation of the whole contract**.
 *
 * The `Content-Type` is deliberately not set: axios composes it from the `FormData` together with the
 * boundary, and writing `multipart/form-data` by hand produces a request with no boundary that the
 * server cannot parse. The CSRF header is already on it, because the single instance's interceptor
 * adds one to every `PUT` (ADR-0010).
 *
 * The client checks the type and the size before sending (the panel does), but **the server's check
 * is the one that decides**: it validates the real content of the file and not its extension
 * (`docs/08-identidad-y-seguridad.md` §9), so `413` and `415` are painted by their `code` like any
 * other answer.
 */
export function useUploadProductImage() {
  const invalidate = useInvalidateCatalog();

  return useMutation({
    mutationFn: ({ id, file }: UploadProductImageVariables) => {
      const body = new FormData();
      body.append('file', file);

      return http
        .put<ProductImage>(`/api/admin/products/${id}/image`, body)
        .then((response) => response.data);
    },
    onSuccess: invalidate,
  });
}

/** Removing the photo. Idempotent and `204`: a product with no photo answers the same as one that had one. */
export function useDeleteProductImage() {
  const invalidate = useInvalidateCatalog();

  return useMutation({
    mutationFn: async (id: number) => {
      await http.delete(`/api/admin/products/${id}/image`);
    },
    onSuccess: invalidate,
  });
}
