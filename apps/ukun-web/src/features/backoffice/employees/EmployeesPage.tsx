import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Container, Table } from 'react-bootstrap';

import { usePageWithinRange } from '../usePageWithinRange';
import { Icon } from '../../../shared/ui/Icon';
import { Pagination } from '../../../shared/ui/Pagination';
import { QueryBoundary } from '../../../shared/ui/QueryBoundary';
import { EmploymentBoundary } from '../components/EmploymentBoundary';
import { useEmployees } from './api/queries';
import { toApiFilters } from './api/employeeKeys';
import { EmployeeFilters } from './components/EmployeeFilters';
import { EmployeeRow } from './components/EmployeeRow';
import { NEW_EMPLOYEE_PATH } from './routes';
import { countActiveFilters, useEmployeeParams } from './useEmployeeParams';

/**
 * The staff of the admin's store (RF-11) at `/admin/employees`.
 *
 * **The store is never in the URL and never in a request.** It is resolved server-side from
 * `account_id`, and the name on the title comes from the session's own employment: an identifier
 * arriving in a request identifies a resource and never authorises access to it
 * (`docs/08-identidad-y-seguridad.md` §4). The screen does not compare stores, and the server
 * refuses somebody else's regardless of what the browser believes.
 *
 * The route is under `/admin/*` and not `/backoffice/*`, where the stock lives: ADR-0020 makes the
 * prefix a biconditional with `/api/admin/**`, and managing employees is an administrator's job
 * while managing stock is any employee's.
 *
 * What is filtered and which page it is on live in the URL and never in a `useState`
 * (`docs/07-frontend-ux-ui.md`): that is what makes a table linkable — which is how the alta points
 * at the employee it just created — survives a reload, and keeps the back button honest.
 *
 * **There is no row action of any kind**, because editing and deleting an employee do not exist in
 * v1.0 (RF-11) — see `EmployeeRow`.
 */
export default function EmployeesPage() {
  const { t } = useTranslation('backoffice');
  const { filters, page, updateFilters, clearFilters, goToPage } = useEmployeeParams();

  const query = useEmployees(toApiFilters(filters, page));

  /**
   * Everything this screen reads off the query, read **here** and not down in the JSX.
   *
   * It is not a style choice. TanStack Query watches which fields of the result a render touches and
   * only re-renders for the ones it saw (tracked properties). The body below is a render prop that
   * `EmploymentBoundary` does not call while the employment is still loading — so reading the status
   * only in there means the first render touches `data` and nothing else, and the read **failing** is
   * a change to fields nobody asked to hear about. The screen then spins forever over a `403` that
   * already came back. `NewStockLinePage` documents the same trap from the other side.
   */
  const employees = query.data?.items;
  const totalPages = query.data?.totalPages;
  const totalElements = query.data?.totalElements;
  const status = { isPending: query.isPending, isError: query.isError, error: query.error };

  usePageWithinRange(page, totalPages, goToPage);

  const isFiltered = countActiveFilters(filters) > 0;

  return (
    <Container className="py-5">
      <EmploymentBoundary>
        {(employment) => (
          <>
            <div className="d-flex flex-wrap align-items-start justify-content-between gap-3 mb-2">
              <div>
                <h1 className="h2 mb-2">
                  {t('employees.title', { store: employment.storeName })}
                </h1>
                <p className="text-body-secondary mb-0">{t('employees.lead')}</p>
              </div>
              {/* Un enlace con aspecto de botón: el destino es una ruta, así que es un `<Link>` y no
                  un `<button>` que navega. */}
              <Link
                to={NEW_EMPLOYEE_PATH}
                className="btn btn-primary d-inline-flex align-items-center gap-1"
              >
                <Icon name="add" size="sm" />
                {t('employees.add')}
              </Link>
            </div>

            <EmployeeFilters filters={filters} onChange={updateFilters} onClear={clearFilters} />

            <p className="text-body-secondary" aria-live="polite">
              {totalElements === undefined
                ? null
                : t('employees.results', { count: totalElements })}
            </p>

            <QueryBoundary
              query={status}
              data={employees}
              empty={isFiltered ? t('employees.empty.filtered') : t('employees.empty.none')}
            >
              {(rows) => (
                <>
                  <Table hover responsive className="align-middle">
                    <caption className="visually-hidden">
                      {t('employees.table.caption', { store: employment.storeName })}
                    </caption>
                    <thead>
                      <tr>
                        <th scope="col">{t('employees.table.name')}</th>
                        <th scope="col">{t('employees.table.lastName')}</th>
                        <th scope="col">{t('employees.table.email')}</th>
                        <th scope="col">{t('employees.table.jobTitle')}</th>
                        <th scope="col">{t('employees.table.role')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {rows.map((employee) => (
                        <EmployeeRow key={employee.id} employee={employee} />
                      ))}
                    </tbody>
                  </Table>

                  <Pagination
                    page={page}
                    totalPages={totalPages ?? 0}
                    label={t('employees.pagination')}
                    onPageChange={goToPage}
                  />
                </>
              )}
            </QueryBoundary>
          </>
        )}
      </EmploymentBoundary>
    </Container>
  );
}
