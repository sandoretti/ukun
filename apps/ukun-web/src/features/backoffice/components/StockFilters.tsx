import type { ChangeEvent } from 'react';
import { useTranslation } from 'react-i18next';

import type { FilterChip } from '../../../shared/ui/ActiveFilterChips';
import { FilterToolbar } from '../../../shared/ui/FilterToolbar';
import { SearchField } from '../../../shared/ui/SearchField';
import { SelectField } from '../../../shared/ui/SelectField';
import type { StockFilterInput, StockView } from '../api/stockKeys';
import { NO_FILTERS } from '../useStockParams';

type StockFiltersProps = {
  filters: StockFilterInput;
  onChange: (patch: Partial<StockFilterInput>) => void;
  onClear: () => void;
};

/**
 * The three views of RF-09, each with the key it is labelled by. Written out rather than composed
 * into `stock.views.${value}`: a key built at runtime is a key the parity test cannot see, and a
 * missing translation would only show up on screen.
 */
const VIEWS = [
  { value: 'STOCKED', labelKey: 'stock.views.stocked' },
  { value: 'NOT_STOCKED', labelKey: 'stock.views.notStocked' },
  { value: 'ALL', labelKey: 'stock.views.all' },
] as const satisfies readonly { value: StockView; labelKey: string }[];

/**
 * How an employee narrows down the store: the name they remember, the SKU they were given, and how
 * much of the store they want to see.
 *
 * **The order is the order of use.** The product name is what somebody reaches for first and what
 * they know without looking anything up, so it leads and takes the width; the SKU is the exact key
 * for when they have it; and what is listed comes last, because it is the least often changed of
 * the three.
 *
 * The two text fields commit when you leave them or press Enter rather than filtering per keystroke
 * — crossing a field is one request, not forty (`docs/07-frontend-ux-ui.md`). What holds the answer
 * is the URL (`useStockParams`), so a filtered table is linkable and survives a reload; that is as
 * true of the view as of the other two, even though it is a select and not a link.
 */
export function StockFilters({ filters, onChange, onClear }: StockFiltersProps) {
  const { t } = useTranslation('backoffice');

  /**
   * What is filtered right now, in words. **The view is not one of them**: choosing between what the
   * store has, what it could have and both is deciding what to look at, not narrowing it down — the
   * same reason it never counted as a filter to clear.
   */
  const chips: FilterChip[] = [
    ...(filters.q ? [{ key: 'q', label: t('stock.chips.q', { value: filters.q }) }] : []),
    ...(filters.sku ? [{ key: 'sku', label: t('stock.chips.sku', { value: filters.sku }) }] : []),
  ];

  return (
    <FilterToolbar
      label={t('stock.filters.label')}
      hint={t('stock.filters.hint')}
      chips={chips}
      onRemove={(key) => onChange({ [key]: NO_FILTERS[key as 'q' | 'sku'] })}
      onClear={onClear}
    >
      <div className="filter-toolbar__search">
        <SearchField
          label={t('stock.filters.q')}
          placeholder={t('stock.filters.qPlaceholder')}
          value={filters.q}
          onCommit={(q) => onChange({ q })}
        />
      </div>

      <SearchField
        label={t('stock.filters.sku')}
        placeholder={t('stock.filters.skuPlaceholder')}
        value={filters.sku}
        onCommit={(sku) => onChange({ sku })}
      />

      <SelectField
        label={t('stock.views.label')}
        labelVisibility="hidden"
        value={filters.status}
        onChange={(event: ChangeEvent<HTMLSelectElement>) =>
          onChange({ status: event.target.value as StockView })
        }
      >
        {VIEWS.map((view) => (
          <option key={view.value} value={view.value}>
            {t(view.labelKey)}
          </option>
        ))}
      </SelectField>
    </FilterToolbar>
  );
}
