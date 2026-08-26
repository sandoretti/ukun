import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { http } from '../../../../shared/api/http';
import { retryUnlessClientError } from '../../../../shared/api/retry';
import {
  employeeKeys,
  type CreateEmployeeRequest,
  type EmployeeFilters,
  type EmployeePage,
} from './employeeKeys';

/**
 * What travels to the API. A filter that was not set is **left out** rather than sent empty: the
 * contract makes both parameters optional and one that is absent is not applied, while `?isAdmin=`
 * would be a value the server has to reject.
 */
function toQueryParams(filters: EmployeeFilters) {
  return {
    ...(filters.q ? { q: filters.q } : {}),
    ...(filters.isAdmin === null ? {} : { isAdmin: filters.isAdmin }),
    page: filters.page,
    size: filters.size,
  };
}

/**
 * The staff of the admin's store, page by page (RF-11).
 *
 * **No store travels with it.** The listing is scoped server-side from the admin's `account_id`, so
 * there is nothing here to pass one to — an identifier arriving in a request identifies a resource
 * and never authorises access to it (`docs/08-identidad-y-seguridad.md` §4).
 *
 * The page number that arrives already counts from zero: the URL counts from one and the
 * translation happens in `toApiFilters`, once. `keepPreviousData` keeps the table on screen while
 * the next page loads instead of collapsing it on every page change (`docs/07-frontend-ux-ui.md`).
 */
export function useEmployees(filters: EmployeeFilters) {
  return useQuery<EmployeePage>({
    queryKey: employeeKeys.list(filters),
    queryFn: ({ signal }) =>
      http
        .get<EmployeePage>('/api/admin/employees', { params: toQueryParams(filters), signal })
        .then((response) => response.data),
    placeholderData: keepPreviousData,
    retry: retryUnlessClientError,
  });
}

/**
 * How far the alta got. Both are successes and the difference between them is real (`docs/08` §3),
 * exactly as in the customer signup this saga reuses:
 *
 * - `completed` (`201`) — the account is active and the new employee can sign in right now.
 * - `activationPending` (`202`) — the row exists but the activation was not confirmed. Repeating
 *   the same alta finishes it, and nothing has been duplicated.
 */
export type EmployeeCreationOutcome = 'completed' | 'activationPending';

/**
 * Creating an employee (RF-11), which is the signup saga with the roles of an employee.
 *
 * **The body carries no `storeId` and there is nowhere to put one**: the new employee's store is
 * always the store of the admin creating them, resolved server-side. Accepting it from the client
 * would turn an authorisation fact into a parameter.
 *
 * The outcome is read off the **status** and not off a body: the `202` has none, because the
 * employee is in the listing either way and that is where the admin looks next. A `409
 * EMAIL_ALREADY_EXISTS` reaches the form as the `ProblemDetailError` the axios instance builds.
 */
export function useCreateEmployee() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: CreateEmployeeRequest): Promise<EmployeeCreationOutcome> =>
      http
        .post<void>('/api/admin/employees', request)
        .then((response) => (response.status === 202 ? 'activationPending' : 'completed')),
    // Both outcomes wrote a row, so both stale the listing — including the `202`, which is the one
    // it would be easy to think changed nothing.
    onSuccess: () => queryClient.invalidateQueries({ queryKey: employeeKeys.lists() }),
  });
}
