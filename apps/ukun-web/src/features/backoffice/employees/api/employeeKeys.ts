import type { components } from '../../../../types/api';

export type Employee = components['schemas']['Employee'];
export type EmployeePage = components['schemas']['EmployeePage'];
export type CreateEmployeeRequest = components['schemas']['CreateEmployeeRequest'];

/**
 * What the listing is filtered by — the part of the URL that is not the page.
 *
 * `isAdmin` is three states and not a boolean: the contract's parameter is optional, so not sending
 * it is "everybody", and sending it is asking for one side or the other. `null` is that absence,
 * written down rather than left to `undefined` so nothing has to guess whether the filter was read
 * and empty or never read at all.
 */
export type EmployeeFilterInput = {
  q: string;
  isAdmin: boolean | null;
};

/** One request to `GET /api/admin/employees`: the filters plus the API's slice, `page` from zero. */
export type EmployeeFilters = EmployeeFilterInput & {
  page: number;
  size: number;
};

/** How many rows a page of the table holds. One request per page; the contract has no sort. */
export const PAGE_SIZE = 20;

/**
 * The listing's state as the API takes it.
 *
 * **This is the one place the page number is translated.** The URL counts from one and the API from
 * zero (`docs/07-frontend-ux-ui.md`), and doing it once is what keeps the table and its pagination
 * from ever disagreeing about which page they are on.
 */
export function toApiFilters(filters: EmployeeFilterInput, page: number): EmployeeFilters {
  return { q: filters.q, isAdmin: filters.isAdmin, page: page - 1, size: PAGE_SIZE };
}

/**
 * Query keys of the admin's staff, hierarchical so invalidating is targeted by prefix.
 *
 * **No store in the key, because no request carries one.** Both operations of RF-11 resolve the
 * store server-side from `account_id`, so there is exactly one staff list a session can ever read —
 * unlike the stock, whose routes take a `{storeId}` and whose cache can therefore hold two.
 */
export const employeeKeys = {
  all: ['employees'] as const,
  lists: () => [...employeeKeys.all, 'list'] as const,
  list: (filters: EmployeeFilters) => [...employeeKeys.lists(), filters] as const,
};
