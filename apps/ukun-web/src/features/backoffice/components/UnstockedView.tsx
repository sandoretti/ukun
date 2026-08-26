import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Table } from 'react-bootstrap';

import { usePageWithinRange } from '../../../shared/hooks/usePageParams';
import { Pagination } from '../../../shared/ui/Pagination';
import { QueryBoundary } from '../../../shared/ui/QueryBoundary';
import { useUnstockedCandidates } from '../api/queries';
import { toApiFilters, type StockFilterInput } from '../api/stockKeys';
import { newStockLinePath } from '../routes';
import { countActiveFilters } from '../useStockParams';

type UnstockedViewProps = {
  storeId: number;
  storeName: string;
  filters: StockFilterInput;
  /** Counting from one, as the URL does. */
  page: number;
  onPageChange: (page: number) => void;
};

/**
 * The catalog products that have **no** row in this store — the "add new stock" half of RF-09.
 *
 * A product without a row is not managed in this store, which is a different thing from a quantity of
 * zero: zero is a line that exists and says there are none left. That is why nothing on this table
 * shows a quantity, and why the only action is a link to the add screen carrying the SKU — creating a
 * line is by SKU, not by internal identifier (ADR-0015).
 *
 * It looks a lot like its sibling and stays a separate component on purpose: neither of the two ever
 * sees the contract's union type, so no row here can be asked for a `lineId` it does not have.
 */
export function UnstockedView({ storeId, storeName, filters, page, onPageChange }: UnstockedViewProps) {
  const { t } = useTranslation('backoffice');

  const query = useUnstockedCandidates(storeId, toApiFilters(filters, page));

  const candidates = query.data?.items;
  const totalPages = query.data?.totalPages;

  usePageWithinRange(page, totalPages, onPageChange);

  const isFiltered = countActiveFilters(filters) > 0;

  return (
    <>
      <p className="text-body-secondary" aria-live="polite">
        {query.data === undefined ? null : t('stock.results', { count: query.data.totalElements })}
      </p>

      <QueryBoundary
        query={query}
        data={candidates}
        empty={isFiltered ? t('stock.empty.notStockedFiltered') : t('stock.empty.notStocked')}
      >
        {(rows) => (
          <>
            <Table hover responsive className="align-middle">
              <caption className="visually-hidden">
                {t('stock.table.caption', { store: storeName })}
              </caption>
              <thead>
                <tr>
                  <th scope="col">{t('stock.table.sku')}</th>
                  <th scope="col">{t('stock.table.product')}</th>
                  <th scope="col" className="text-end">
                    {t('stock.table.actions')}
                  </th>
                </tr>
              </thead>
              <tbody>
                {rows.map((candidate) => (
                  <tr key={candidate.productId}>
                    <td className="text-tabular-nums">{candidate.sku}</td>
                    <td>{candidate.productName}</td>
                    <td className="text-end">
                      <Link
                        to={newStockLinePath(candidate.sku)}
                        className="btn btn-outline-primary btn-sm"
                        aria-label={t('stock.row.add', { product: candidate.productName })}
                      >
                        {t('stock.add')}
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </Table>

            <Pagination
              page={page}
              totalPages={totalPages ?? 0}
              label={t('stock.pagination')}
              onPageChange={onPageChange}
            />
          </>
        )}
      </QueryBoundary>
    </>
  );
}
