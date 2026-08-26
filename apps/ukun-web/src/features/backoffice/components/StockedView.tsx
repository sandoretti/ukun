import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Table } from 'react-bootstrap';

import { usePageWithinRange } from '../../../shared/hooks/usePageParams';
import { Pagination } from '../../../shared/ui/Pagination';
import { QueryBoundary } from '../../../shared/ui/QueryBoundary';
import { useStockedLines } from '../api/queries';
import { toApiFilters, type StockFilterInput } from '../api/stockKeys';
import { countActiveFilters } from '../useStockParams';
import { DeleteStockLineModal } from './DeleteStockLineModal';
import { EditQuantityModal } from './EditQuantityModal';
import { StockedRow } from './StockedRow';

type StockedViewProps = {
  storeId: number;
  storeName: string;
  filters: StockFilterInput;
  /** Counting from one, as the URL does. */
  page: number;
  onPageChange: (page: number) => void;
};

/**
 * The stock this store holds, and the two writes that act on one line of it.
 *
 * **The dialogs live here and not in the row**, and the line they are given is looked up in the last
 * answer from the server on every render. That is what makes the version conflict work: when a `409`
 * makes the mutation re-read the listing, the fresh row reaches the open dialog by itself. A dialog
 * holding its own copy of the line would be showing the stale numbers it was opened with — which are
 * exactly the ones that lost the race.
 *
 * It is also why a line deleted by somebody else turns into `undefined` here rather than into a
 * dialog that stays open over a row that is gone.
 */
export function StockedView({ storeId, storeName, filters, page, onPageChange }: StockedViewProps) {
  const { t } = useTranslation('backoffice');
  const [editingLineId, setEditingLineId] = useState<number | null>(null);
  const [deletingLineId, setDeletingLineId] = useState<number | null>(null);

  const query = useStockedLines(storeId, toApiFilters(filters, page));

  const lines = query.data?.items;
  const totalPages = query.data?.totalPages;

  usePageWithinRange(page, totalPages, onPageChange);

  const editing = lines?.find((line) => line.lineId === editingLineId);
  const deleting = lines?.find((line) => line.lineId === deletingLineId);

  const isFiltered = countActiveFilters(filters) > 0;

  return (
    <>
      <p className="text-body-secondary" aria-live="polite">
        {query.data === undefined ? null : t('stock.results', { count: query.data.totalElements })}
      </p>

      <QueryBoundary
        query={query}
        data={lines}
        empty={isFiltered ? t('stock.empty.stockedFiltered') : t('stock.empty.stocked')}
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
                    {t('stock.table.quantity')}
                  </th>
                  <th scope="col" className="text-end">
                    {t('stock.table.actions')}
                  </th>
                </tr>
              </thead>
              <tbody>
                {rows.map((line) => (
                  <StockedRow
                    key={line.lineId}
                    line={line}
                    onEdit={(target) => setEditingLineId(target.lineId)}
                    onDelete={(target) => setDeletingLineId(target.lineId)}
                  />
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

      {editingLineId !== null && (
        <EditQuantityModal
          storeId={storeId}
          line={editing}
          isRefreshing={query.isFetching}
          onClose={() => setEditingLineId(null)}
        />
      )}

      {deletingLineId !== null && (
        <DeleteStockLineModal
          storeId={storeId}
          storeName={storeName}
          line={deleting}
          onClose={() => setDeletingLineId(null)}
        />
      )}
    </>
  );
}
