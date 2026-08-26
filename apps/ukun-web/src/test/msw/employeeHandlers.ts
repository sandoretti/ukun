import { http, HttpResponse } from 'msw';

import type { components } from '../../types/api';
import { normalise } from './catalogHandlers';
import { paged } from './paged';
import { problem } from './problem';
import { employmentAt } from './stockHandlers';

/**
 * Test-only stand-in for the employee endpoints of `ukun-api` (RF-11), typed from the same generated
 * OpenAPI types the app uses.
 *
 * Like the stock's, this double **keeps state**: the point of the alta is that it changes what the
 * next listing answers, and a stateless stub would let a test pass while the cache was never
 * refreshed. The state belongs to each `employeeState(...)` call, so it dies with the test that
 * installed it.
 *
 * **Neither endpoint takes a store**, exactly as the contract has it: the listing is scoped and the
 * alta is placed by the server from the admin's `account_id`, so a request that tried to name one
 * would have nowhere to put it. It reuses `employmentAt()` because the screens read the store's name
 * from the same `GET /api/me/employment` the stock ones do.
 */

type Employee = components['schemas']['Employee'];
type CreateEmployeeRequest = components['schemas']['CreateEmployeeRequest'];
type ErrorCode = components['schemas']['ErrorCode'];
type ValidationError = components['schemas']['ValidationError'];

const base = import.meta.env.VITE_API_URL || '';

/** The store every test works against unless it says otherwise — the same one the stock double uses. */
const STORE_ID = 1;

/** A row of the store's staff, as the database would hold it. */
export type SeedEmployee = Omit<Employee, 'storeId'> & { storeId?: number };

/** The three employees of the seeded store, one of them an administrator (RF-12). */
export const staff: SeedEmployee[] = [
  { id: 1, name: 'Joel', lastName: 'Sandoval', email: 'joel@ukun.dev', jobTitle: 'Gerente', isAdmin: true },
  { id: 2, name: 'Daniel', lastName: 'García', email: 'daniel@ukun.dev', jobTitle: 'Vendedor', isAdmin: false },
  { id: 3, name: 'Lucía', lastName: 'Muñoz', email: 'lucia@ukun.dev', jobTitle: 'Vendedora', isAdmin: false },
];

/**
 * The admin's staff with a life of its own for one test:
 * `server.use(...employeeState({ employees: staff }).handlers)`.
 *
 * It reproduces the contract: the listing filters by free text over name, last name and email and by
 * the administrator flag, and pages; the `POST` answers `201` with the new employee, or `409
 * EMAIL_ALREADY_EXISTS` when the email is taken — by a seeded row or by one this same test created.
 *
 * `activationPending` turns the alta into the contract's other success, the `202` with no body.
 */
export function employeeState(
  options: { employees?: readonly SeedEmployee[]; activationPending?: boolean } = {},
) {
  let rows: Employee[] = (options.employees ?? []).map((employee) => ({
    ...employee,
    storeId: employee.storeId ?? STORE_ID,
  }));
  let nextId = Math.max(0, ...rows.map((row) => row.id)) + 1;

  /** How many altas actually reached the server — what proves client-side validation stopped one. */
  const counters = { created: 0 };
  /** Every body the `POST` was sent, so a test can check what travelled without reading the screen. */
  const bodies: CreateEmployeeRequest[] = [];
  /** Every listing query string, in order: what proves a filter was applied by the server, not here. */
  const searches: string[] = [];

  const handlers = [
    employmentAt({ storeId: STORE_ID }),

    http.get(`${base}/api/admin/employees`, ({ request }) => {
      const search = new URL(request.url).searchParams;
      searches.push(search.toString());

      const term = search.get('q');
      const isAdmin = search.get('isAdmin');
      const page = Number(search.get('page') ?? 0);
      const size = Number(search.get('size') ?? 20);

      const matched = rows
        .filter((row) =>
          term
            ? [row.name, row.lastName, row.email].some((field) =>
                normalise(field).includes(normalise(term)),
              )
            : true,
        )
        .filter((row) => (isAdmin === null ? true : row.isAdmin === (isAdmin === 'true')));

      return HttpResponse.json(paged(matched, page, size));
    }),

    http.post(`${base}/api/admin/employees`, async ({ request }) => {
      const body = (await request.json()) as CreateEmployeeRequest;
      bodies.push(body);

      // Uniqueness belongs to ukun-auth, which is why this is a `code` of its own and not a field
      // error: only the service that owns the accounts can say the email is taken.
      if (rows.some((row) => row.email === body.email)) {
        return problem('EMAIL_ALREADY_EXISTS', 409);
      }

      const created: Employee = {
        id: nextId,
        name: body.name,
        lastName: body.lastName,
        email: body.email,
        jobTitle: body.jobTitle,
        isAdmin: body.isAdmin ?? false,
        // The store nobody chose: resolved from the admin's account, never from the body.
        storeId: STORE_ID,
      };
      nextId += 1;
      rows = [...rows, created];
      counters.created += 1;

      if (options.activationPending === true) {
        // `202`: the row exists and the account does not answer yet. No body, as the contract has it.
        return new HttpResponse(null, { status: 202 });
      }

      return HttpResponse.json(created, {
        status: 201,
        headers: { Location: `/api/admin/employees/${created.id}` },
      });
    }),
  ];

  return { handlers, counters, bodies, searches };
}

/** The listing failing, by `code` — the screen has nothing to show and paints the error state. */
export function employeesFail(code: ErrorCode, status: number) {
  return http.get(`${base}/api/admin/employees`, () => problem(code, status));
}

/** The alta rejected on a field: the `400 VALIDATION_FAILED` of `docs/12-error-contract.md`. */
export function createEmployeeRejects(errors: ValidationError[]) {
  return http.post(`${base}/api/admin/employees`, () =>
    problem('VALIDATION_FAILED', 400, { errors }),
  );
}
