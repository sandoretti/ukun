import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Container, Table } from 'react-bootstrap';

import { Icon } from '../../shared/ui/Icon';
import { Pagination } from '../../shared/ui/Pagination';
import { QueryBoundary } from '../../shared/ui/QueryBoundary';
import { NEW_PRODUCT_PATH } from './adminRoutes';
import { toApiFilters } from './api/adminProductKeys';
import { useAdminProducts } from './api/adminProductQueries';
import { AdminProductFilters } from './components/AdminProductFilters';
import { AdminProductRow } from './components/AdminProductRow';
import { countActiveFilters, useAdminProductParams } from './useAdminProductParams';
import { usePageWithinRange } from './useStockParams';

/**
 * The catalog as the back-office sees it (RF-10) at `/admin/products`.
 *
 * **It is not the public listing with a different skin.** This one answers with discontinued products
 * too, which is not a detail: a product pulled from the catalog can only be reactivated by someone who
 * can find it first (ADR-0013), and the `status` filter is the whole reason this screen exists next to
 * `/products`.
 *
 * Filters, order and page live in the URL and never in a `useState` (`docs/07-frontend-ux-ui.md`):
 * that is what makes a filtered table linkable —which is how the add screen points at the product it
 * just created— survives a reload, and keeps the back button honest.
 *
 * The route hangs off `/admin/*` and not `/backoffice/*` because it talks to `/api/admin/**`
 * (ADR-0020) and is an administrator's alone, unlike the stock screens.
 */
export default function AdminProductsPage() {
  const { t } = useTranslation('backoffice');
  const { filters, page, updateFilters, clearFilters, goToPage } = useAdminProductParams();

  const query = useAdminProducts(toApiFilters(filters, page));

  const products = query.data?.items;
  const totalPages = query.data?.totalPages;

  usePageWithinRange(page, totalPages, goToPage);

  const isFiltered = countActiveFilters(filters) > 0;

  return (
    <Container className="py-5">
      <div className="d-flex flex-wrap align-items-start justify-content-between gap-3 mb-2">
        <div>
          <h1 className="h2 mb-2">{t('products.title')}</h1>
          <p className="text-body-secondary mb-0">{t('products.lead')}</p>
        </div>
        {/* A link that looks like a button: the destination is a route, so it is a `<Link>` and not a
            `<button>` that navigates. */}
        <Link to={NEW_PRODUCT_PATH} className="btn btn-primary d-inline-flex align-items-center gap-1">
          <Icon name="add" size="sm" />
          {t('products.add')}
        </Link>
      </div>

      <AdminProductFilters filters={filters} onChange={updateFilters} onClear={clearFilters} />

      <p className="text-body-secondary" aria-live="polite">
        {query.data === undefined ? null : t('products.results', { count: query.data.totalElements })}
      </p>

      <QueryBoundary
        query={query}
        data={products}
        empty={isFiltered ? t('products.empty.filtered') : t('products.empty.none')}
      >
        {(rows) => (
          <>
            <Table hover responsive className="align-middle">
              <caption className="visually-hidden">{t('products.table.caption')}</caption>
              <thead>
                <tr>
                  <th scope="col">
                    <span className="visually-hidden">{t('products.table.photo')}</span>
                  </th>
                  <th scope="col">{t('products.table.sku')}</th>
                  <th scope="col">{t('products.table.name')}</th>
                  <th scope="col">{t('products.table.type')}</th>
                  <th scope="col" className="text-end">
                    {t('products.table.price')}
                  </th>
                  <th scope="col">{t('products.table.status')}</th>
                  <th scope="col" className="text-end">
                    {t('products.table.actions')}
                  </th>
                </tr>
              </thead>
              <tbody>
                {rows.map((product) => (
                  <AdminProductRow key={product.id} product={product} />
                ))}
              </tbody>
            </Table>

            <Pagination
              page={page}
              totalPages={totalPages ?? 0}
              label={t('products.pagination')}
              onPageChange={goToPage}
            />
          </>
        )}
      </QueryBoundary>
    </Container>
  );
}
