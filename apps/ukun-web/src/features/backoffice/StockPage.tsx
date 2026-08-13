import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Container } from 'react-bootstrap';

import { Icon } from '../../shared/ui/Icon';
import { EmploymentBoundary } from './components/EmploymentBoundary';
import { StockedView } from './components/StockedView';
import { StockFilters } from './components/StockFilters';
import { StockStatusTabs } from './components/StockStatusTabs';
import { UnstockedView } from './components/UnstockedView';
import { NEW_STOCK_LINE_PATH } from './routes';
import { useStockParams } from './useStockParams';

/**
 * The stock of the employee's store (RF-09) at `/backoffice/stock`.
 *
 * **The store is never in the URL.** It is resolved from the session, once, by `EmploymentBoundary`;
 * the route carries no identifier because an identifier arriving in a request identifies a resource
 * and never authorises access to it (`docs/08-identidad-y-seguridad.md` §4). The screen does not
 * compare stores, and the server refuses somebody else's with a `403` regardless of what the browser
 * believes.
 *
 * Which half is listed, what is filtered and which page it is on all live in the URL and never in a
 * `useState` (`docs/07-frontend-ux-ui.md`): that is what makes a table linkable — which is how the
 * add screen points at the line it just created — survives a reload, and keeps the back button
 * honest.
 *
 * The route is under `/backoffice/*` and not `/admin/*`: managing stock belongs to any employee, not
 * to an administrator, and it talks to `/api/stores/**`, not to `/api/admin/**` (ADR-0020).
 */
export default function StockPage() {
  const { t } = useTranslation('backoffice');
  const { filters, page, updateFilters, clearFilters, goToPage, hrefForStatus } = useStockParams();

  return (
    <Container className="py-5">
      <EmploymentBoundary>
        {(employment) => (
          <>
            <div className="d-flex flex-wrap align-items-start justify-content-between gap-3 mb-2">
              <div>
                <h1 className="h2 mb-2">{t('stock.title', { store: employment.storeName })}</h1>
                <p className="text-body-secondary mb-0">{t('stock.lead')}</p>
              </div>
              {/* Un enlace con aspecto de botón, como el alta de la barra: el destino es una ruta,
                  así que es un `<Link>` y no un `<button>` que navega. */}
              <Link
                to={NEW_STOCK_LINE_PATH}
                className="btn btn-primary d-inline-flex align-items-center gap-1"
              >
                <Icon name="add" size="sm" />
                {t('stock.add')}
              </Link>
            </div>

            <StockStatusTabs current={filters.status} hrefForStatus={hrefForStatus} />

            <StockFilters filters={filters} onChange={updateFilters} onClear={clearFilters} />

            {filters.status === 'STOCKED' ? (
              <StockedView
                storeId={employment.storeId}
                storeName={employment.storeName}
                filters={filters}
                page={page}
                onPageChange={goToPage}
              />
            ) : (
              <UnstockedView
                storeId={employment.storeId}
                storeName={employment.storeName}
                filters={filters}
                page={page}
                onPageChange={goToPage}
              />
            )}
          </>
        )}
      </EmploymentBoundary>
    </Container>
  );
}
