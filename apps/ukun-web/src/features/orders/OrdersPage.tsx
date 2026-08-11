import { useTranslation } from 'react-i18next';
import { Col, Container, Row } from 'react-bootstrap';

import { Pagination } from '../../shared/ui/Pagination';
import { QueryBoundary } from '../../shared/ui/QueryBoundary';
import { PAGE_SIZE } from './api/orderKeys';
import { useOrders } from './api/queries';
import { OrderCard } from './components/OrderCard';
import { OrderDateFilters } from './components/OrderDateFilters';
import { useOrderParams } from './useOrderParams';

/**
 * The customer's order history (RF-04, query Q3) at `/orders`, behind `RequireAuth`: most recent
 * first, filterable by date range and paged server-side.
 *
 * The filters and the page live in the URL, never in a `useState` (`docs/07-frontend-ux-ui.md`), so a
 * filtered range is linkable and the back button does what the reader expects. Nothing is filtered or
 * sorted in the browser: the order and the slice are the server's.
 */
export default function OrdersPage() {
  const { t } = useTranslation('orders');
  const { filters, page, updateFilters, clearFilters, goToPage } = useOrderParams();

  // The URL counts pages from one and the API from zero; the translation happens here and nowhere
  // else, which is what keeps `?page=1` from meaning the second page.
  const ordersQuery = useOrders({ ...filters, page: page - 1, size: PAGE_SIZE });
  const totalPages = ordersQuery.data?.totalPages ?? 0;

  return (
    <Container className="py-5">
      <Row>
        <Col lg={9} xl={8}>
          <h1 className="h2 mb-2">{t('title')}</h1>
          <p className="text-body-secondary mb-4">{t('lead')}</p>

          <OrderDateFilters filters={filters} onChange={updateFilters} onClear={clearFilters} />

          <QueryBoundary query={ordersQuery} data={ordersQuery.data?.items} empty={t('empty')}>
            {(orders) => (
              <>
                <ul className="order-list list-unstyled mb-0">
                  {orders.map((order) => (
                    <li key={order.id}>
                      <OrderCard order={order} />
                    </li>
                  ))}
                </ul>
                <Pagination
                  page={page}
                  totalPages={totalPages}
                  label={t('pagination')}
                  onPageChange={goToPage}
                />
              </>
            )}
          </QueryBoundary>
        </Col>
      </Row>
    </Container>
  );
}
