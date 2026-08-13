import { useQuery } from '@tanstack/react-query';

import { http } from '../../../shared/api/http';

/**
 * Which store the employee behind this session works in — the one thing every screen of this feature
 * needs before it can ask anything, because the four stock routes carry `{storeId}` in the path.
 *
 * **This type is written by hand, on purpose, and this is the only file that does it.**
 * `GET /api/me/employment` is agreed but not yet in `docs/api/openapi/ukun-api.yaml`, so
 * `openapi-typescript` does not generate it. The day the contract carries it, this becomes one line:
 *
 * ```ts
 * export type Employment = components['schemas']['Employment'];
 * ```
 *
 * and no other file changes. It is an explicit exception to the rule that types come from the
 * contract (skill `react-web`, rule 7), taken so the screens could be built while the contract
 * change is done elsewhere — not a licence to hand-write the next one.
 *
 * It is a resource of its own rather than a field on `CurrentUser` because `/api/me` is served by the
 * `customers` module and the employee's store lives in `inventory`/`staff`: adding it there would
 * make `customers` depend on employee data, which `ApplicationModules.verify()` checks (ADR-0017).
 */
export type Employment = {
  employeeId: number;
  storeId: number;
  storeName: string;
};

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
