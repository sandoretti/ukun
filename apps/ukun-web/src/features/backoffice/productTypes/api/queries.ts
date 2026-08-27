import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { http } from '../../../../shared/api/http';
import { productTypeKeys } from '../../../../shared/api/productTypes';
import { retryUnlessClientError } from '../../../../shared/api/retry';
import { adminProductTypeKeys, type AdminProductType, type ProductTypeRequest } from './productTypeKeys';

const ENDPOINT = '/api/admin/product-types';

/**
 * What every write stales — **both** lists of types, not just this screen's.
 *
 * The public one (`shared/api/productTypes.ts`) is cached for an hour on purpose: it is a small
 * closed set that the catalog filter, the landing's categories and the product form's selector all
 * read. That `staleTime` is exactly why it has to be invalidated from here: without this line a type
 * created in the back-office would be invisible in the alta of a product for the next hour, on the
 * very screen the roadmap says is used right after this one.
 *
 * It reaches across to `shared/` and not into another feature, which is the import direction the
 * frontend allows (`docs/07-frontend-ux-ui.md`).
 */
function useInvalidateTypes() {
  const queryClient = useQueryClient();

  return async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: adminProductTypeKeys.all }),
      queryClient.invalidateQueries({ queryKey: productTypeKeys.all }),
    ]);
  };
}

/**
 * The catalog's types with their `productCount` (RF-13).
 *
 * A flat array and not a page: the contract has no parameters here because the set is small and
 * closed, so there is nothing to filter, sort or page through — the server already sorts it by name.
 */
export function useAdminProductTypes() {
  return useQuery({
    queryKey: adminProductTypeKeys.lists(),
    queryFn: ({ signal }) =>
      http.get<AdminProductType[]>(ENDPOINT, { signal }).then((response) => response.data),
    retry: retryUnlessClientError,
  });
}

/**
 * Creating a type. It is born with `productCount: 0` by definition, and a name already taken comes
 * back as `409 DUPLICATE_VALUE` naming the field that clashed.
 */
export function useCreateProductType() {
  const invalidate = useInvalidateTypes();

  return useMutation({
    mutationFn: (request: ProductTypeRequest) =>
      http.post<AdminProductType>(ENDPOINT, request).then((response) => response.data),
    onSuccess: invalidate,
  });
}

/**
 * Renaming a type, which is the only edit a type has.
 *
 * **It rewrites no history.** What an order line freezes is the *product*'s name, not its type
 * (ADR-0011, `docs/10-modelo-de-datos.md` §5) — unlike the legacy order, which shared the live row
 * with the customer's profile. What it does change immediately is the public catalog's filter, which
 * is why the public list is invalidated too.
 */
export function useRenameProductType() {
  const invalidate = useInvalidateTypes();

  return useMutation({
    mutationFn: ({ id, ...request }: ProductTypeRequest & { id: number }) =>
      http.put<AdminProductType>(`${ENDPOINT}/${id}`, request).then((response) => response.data),
    onSuccess: invalidate,
  });
}

/**
 * Deleting a type, which is physical and has no logical variant (ADR-0013): a type with no products
 * is referenced by nothing, so there is no state it could get stuck in. A type **with** products is
 * refused with `409 PRODUCT_TYPE_IN_USE` and its `inProducts`; the way out is reassigning them.
 */
export function useDeleteProductType() {
  const invalidate = useInvalidateTypes();

  return useMutation({
    mutationFn: async (id: number) => {
      await http.delete(`${ENDPOINT}/${id}`);
    },
    onSuccess: invalidate,
  });
}
