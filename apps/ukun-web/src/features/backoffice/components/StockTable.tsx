import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Table } from 'react-bootstrap';

import { usePageWithinRange } from '../usePageWithinRange';
import { Pagination } from '../../../shared/ui/Pagination';
import { QueryBoundary } from '../../../shared/ui/QueryBoundary';
import { useStockView } from '../api/queries';
import { isStocked, toApiFilters, type StockFilterInput, type StockView } from '../api/stockKeys';
import { countActiveFilters } from '../useStockParams';
import { DeleteStockLineModal } from './DeleteStockLineModal';
import { EditQuantityModal } from './EditQuantityModal';
import { StockRow } from './StockRow';

/**
 * Why each view can come back empty, one key per view. Written out rather than composed into
 * `stock.empty.${view}`: a key built at runtime is a key the parity test cannot see, and a missing
 * translation would only show up on screen.
 */
const EMPTY_KEYS = {
  STOCKED: 'stock.empty.STOCKED',
  NOT_STOCKED: 'stock.empty.NOT_STOCKED',
  ALL: 'stock.empty.ALL',
} as const satisfies Record<StockView, string>;

type StockTableProps = {
  storeId: number;
  storeName: string;
  filters: StockFilterInput;
  /** Counting from one, as the URL does. */
  page: number;
  onPageChange: (page: number) => void;
};

/**
 * The store's stock, in whichever of the three views is on screen, and the two writes that act on
 * one line of it.
 *
 * It replaces the two sibling tables this screen used to have. They were separate so that neither
 * ever saw the contract's union type; what makes one table safe now is `StockRow`, which narrows
 * per row — a product with no line cannot be asked for a `lineId` it does not have, because that
 * branch never renders one.
 *
 * **The dialogs live here and not in the row**, and the line they are given is looked up in the last
 * answer from the server on every render. That is what makes the version conflict work: when a `409`
 * makes the mutation re-read the listing, the fresh row reaches the open dialog by itself. A dialog
 * holding its own copy of the line would be showing the stale numbers it was opened with — which are
 * exactly the ones that lost the race.
 */
export function StockTable({ storeId, storeName, filters, page, onPageChange }: StockTableProps) {
  const { t } = useTranslation('backoffice');
  const [editingLineId, setEditingLineId] = useState<number | null>(null);
  const [deletingLineId, setDeletingLineId] = useState<number | null>(null);

  const query = useStockView(storeId, filters.status, toApiFilters(filters, page));

  const lines = query.items;
  const totalPages = query.totalPages;
  const totalElements = query.totalElements;

  usePageWithinRange(page, totalPages, onPageChange);

  // Only a line that exists can be edited or deleted, so the dialogs are looked up among those:
  // a candidate has no `lineId` and there is nothing for either dialog to act on.
  const openLine = (lineId: number | null) =>
    lineId === null ? undefined : lines?.filter(isStocked).find((line) => line.lineId === lineId);

  const editing = openLine(editingLineId);
  const deleting = openLine(deletingLineId);

  const isFiltered = countActiveFilters(filters) > 0;

  return (
    <>
      <p className="text-body-secondary" aria-live="polite">
        {totalElements === undefined ? null : t('stock.results', { count: totalElements })}
      </p>

      <QueryBoundary
        query={query}
        data={lines}
        empty={isFiltered ? t('stock.empty.filtered') : t(EMPTY_KEYS[filters.status])}
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
                  <StockRow
                    // The product is what a row is about, and it is the one identifier both halves
                    // carry: a candidate has no `lineId`.
                    key={line.productId}
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
