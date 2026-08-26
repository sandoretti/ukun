import { useTranslation } from 'react-i18next';

import { FilterToolbar } from '../../../shared/ui/FilterToolbar';
import { SearchField } from '../../../shared/ui/SearchField';
import type { StockFilterInput, StockView } from '../api/stockKeys';
import { countActiveFilters } from '../useStockParams';
import { StockViewSwitch } from './StockViewSwitch';

type StockFiltersProps = {
  filters: StockFilterInput;
  onChange: (patch: Partial<StockFilterInput>) => void;
  onClear: () => void;
  hrefForView: (view: StockView) => string;
};

/**
 * The two ways an employee looks for a product: the SKU they were given, and the name they remember.
 *
 * Both commit when you leave them or press Enter rather than filtering per keystroke — crossing the
 * field is one request, not forty (`docs/07-frontend-ux-ui.md`). What holds the answer is the URL
 * (`useStockParams`), so a filtered table is linkable and survives a reload.
 *
 * They share the toolbar of the staff listing because they are the same kind of bar over the same
 * kind of table; what changes is which controls it holds.
 *
 * The view switch rides in the same bar rather than floating over it as a strip of tabs: choosing
 * what is listed and narrowing it down are the same gesture from the reader's side, and two rows of
 * controls for one job was the thing that made this screen look assembled rather than designed.
 */
export function StockFilters({ filters, onChange, onClear, hrefForView }: StockFiltersProps) {
  const { t } = useTranslation('backoffice');

  return (
    <FilterToolbar
      label={t('stock.filters.label')}
      hint={t('stock.filters.hint')}
      activeCount={countActiveFilters(filters)}
      onClear={onClear}
    >
      <StockViewSwitch current={filters.status} hrefForView={hrefForView} />

      <SearchField
        label={t('stock.filters.sku')}
        placeholder={t('stock.filters.skuPlaceholder')}
        value={filters.sku}
        onCommit={(sku) => onChange({ sku })}
      />

      <div className="filter-toolbar__search">
        <SearchField
          label={t('stock.filters.q')}
          placeholder={t('stock.filters.qPlaceholder')}
          value={filters.q}
          onCommit={(q) => onChange({ q })}
        />
      </div>
    </FilterToolbar>
  );
}
