import { useQuery } from '@tanstack/react-query';

import { http } from '../../../shared/api/http';
import type { components } from '../../../types/api';

/**
 * Which store the employee behind this session works in — the one thing every screen of this feature
 * needs before it can ask anything, because the four stock routes carry `{storeId}` in the path.
 *
 * It is a resource of its own rather than a field on `CurrentUser` because `/api/me` is served by the
 * `customers` module and the employee's store lives in `staff`: adding it there would make
 * `customers` depend on employee data, which `ApplicationModules.verify()` checks (ADR-0017).
 */
export type Employment = components['schemas']['Employment'];

export const employmentKeys = {
  all: ['employment'] as const,
  me: () => [...employmentKeys.all, 'me'] as const,
};

/**
 * The store of the employee looking at the screen.
 *
 * A `403` here is the server's final answer — the account has no employee row — so it is not
 * retried: an account does not become an employee by asking again. And `staleTime: Infinity` because
 * being moved to another store is not something that happens while the tab is open; the day it does,
 * the server denies the old store on the very next request anyway (`EmployeeStoreResolver`).
 *
 * **The store it returns is the only one the screens ever use.** Nothing in this feature reads a
 * store identifier from the URL or compares one by hand: `{storeId}` identifies a resource, it never
 * authorises access to it (`docs/08-identidad-y-seguridad.md` §4).
 */
export function useMyEmployment() {
  return useQuery({
    queryKey: employmentKeys.me(),
    queryFn: ({ signal }) =>
      http.get<Employment>('/api/me/employment', { signal }).then((response) => response.data),
    retry: false,
    staleTime: Infinity,
  });
}
