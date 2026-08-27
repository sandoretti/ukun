import { ADMIN_PATH } from '../../../shared/routes';
import { FIRST_PAGE } from '../routes';
import type { EmployeeFilterInput } from './api/employeeKeys';

/**
 * The staff listing. It hangs off the admin branch and stays in this feature, where `sections.ts`
 * picks it up: that list is what both the back-office rail and its index draw, so this path travels
 * without `shared/routes.ts` having to know it exists.
 */
export const ADMIN_EMPLOYEES_PATH = `${ADMIN_PATH}/employees`;

/**
 * The paths of the employee screens, and the one place that knows what one of their URLs looks like.
 *
 * The branch is `/admin/*` and deliberately not `/backoffice/*`, which is where the stock lives:
 * ADR-0020 makes the prefix a biconditional with `/api/admin/**`, and managing employees is an
 * administrator's job while managing stock is any employee's (RF-11 vs RF-09).
 */
export const NEW_EMPLOYEE_PATH = `${ADMIN_EMPLOYEES_PATH}/new`;

/**
 * The listing's query string. Defaults are left out: `/admin/employees` and `?page=1` are the same
 * screen, and only one of the two is worth putting in somebody's address bar.
 */
export function employeesSearch(
  filters: Partial<EmployeeFilterInput>,
  page: number = FIRST_PAGE,
): URLSearchParams {
  const params = new URLSearchParams();

  if (filters.q) {
    params.set('q', filters.q);
  }
  // `null` is "everybody" and is the absence of the parameter; `false` is a filter like any other
  // and has to survive the trip, which is why this is not a truthiness check.
  if (filters.isAdmin !== undefined && filters.isAdmin !== null) {
    params.set('isAdmin', String(filters.isAdmin));
  }
  if (page > FIRST_PAGE) {
    params.set('page', String(page));
  }

  return params;
}

/**
 * The listing, filtered. It is how the alta sends the reader to the employee it just created without
 * inventing a way to address one: filtering by their email leaves exactly that row on screen.
 */
export function employeesPath(
  filters: Partial<EmployeeFilterInput> = {},
  page: number = FIRST_PAGE,
): string {
  const query = employeesSearch(filters, page).toString();

  return query === '' ? ADMIN_EMPLOYEES_PATH : `${ADMIN_EMPLOYEES_PATH}?${query}`;
}
