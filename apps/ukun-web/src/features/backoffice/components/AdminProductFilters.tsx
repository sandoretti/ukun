import type { ChangeEvent } from 'react';
import { useTranslation } from 'react-i18next';

import { useProductTypes } from '../../../shared/api/productTypes';
import type { FilterChip } from '../../../shared/ui/ActiveFilterChips';
import { FilterToolbar } from '../../../shared/ui/FilterToolbar';
import { SearchField } from '../../../shared/ui/SearchField';
import { SelectField } from '../../../shared/ui/SelectField';
import type {
  AdminProductFilterInput,
  AdminProductSort,
  ProductStatusFilter,
} from '../api/adminProductKeys';
import { NO_FILTERS } from '../useAdminProductParams';

const STATUSES: ProductStatusFilter[] = ['ACTIVE', 'DISCONTINUED', 'ALL'];

const SORTS: AdminProductSort[] = ['NAME_ASC', 'NAME_DESC', 'PRICE_ASC', 'PRICE_DESC', 'UPDATED_DESC'];

/** The keys this screen can drop one at a time. The sort order is not one of them — see below. */
type RemovableFilter = 'q' | 'sku' | 'typeId' | 'status';

type AdminProductFiltersProps = {
  filters: AdminProductFilterInput;
  onChange: (patch: Partial<AdminProductFilterInput>) => void;
  onClear: () => void;
};

/**
 * How an admin narrows the catalog down: the SKU off a label, the name they remember, the type, and
 * —the one this screen exists for— whether they are looking at what is on sale, at what has been
 * pulled, or at both (RF-10).
 *
 * It filters **the same way the other two back-office listings do**, through `FilterToolbar`: one
 * row of controls that name themselves, the explanation under the bar, and the active filters as
 * chips below it. It used to be a `<legend>` over two rows of labelled controls with a lone «Quitar
 * filtros» link, which is not worse — it is *different*, and an administrator sees all three screens
 * (issue #148).
 *
 * The chips are the part worth the migration. A link said neither **what** was filtered —which
 * matters most for `status`, the filter somebody is likeliest to forget they left on— nor let anybody
 * drop just one of four.
 *
 * **The sort order has no chip**, exactly as the stock's view has none: ordering a table is not
 * narrowing it. It is also why `clearFilters` keeps it, and «Limpiar todo» must not rearrange a table
 * nobody asked to rearrange.
 *
 * The two typed fields commit when you leave them or press Enter and never per keystroke — crossing a
 * field is one request, not forty. The closed lists commit on change, because choosing from a list
 * *is* the decision. What holds the answer is the URL, so a filtered table is linkable and survives a
 * reload.
 */
export function AdminProductFilters({ filters, onChange, onClear }: AdminProductFiltersProps) {
  const { t } = useTranslation('backoffice');
  const types = useProductTypes();

  const selectedType = (types.data ?? []).find((type) => type.id === filters.typeId);

  /**
   * What is filtered right now, in words.
   *
   * The type says its **name** and not its id, which is the whole point of a chip — and it waits for
   * the list to arrive rather than showing a number in the meantime, the same way the shop's own
   * `ActiveFilters` does. The status says which of the three views it is asking for.
   */
  const chips: FilterChip[] = [
    ...(filters.q ? [{ key: 'q', label: t('products.chips.q', { value: filters.q }) }] : []),
    ...(filters.sku ? [{ key: 'sku', label: t('products.chips.sku', { value: filters.sku }) }] : []),
    ...(selectedType === undefined
      ? []
      : [{ key: 'typeId', label: t('products.chips.type', { value: selectedType.name }) }]),
    ...(filters.status === NO_FILTERS.status
      ? []
      : [
          {
            key: 'status',
            label: t('products.chips.status', {
              value: t(`products.statusFilter.${filters.status}`),
            }),
          },
        ]),
  ];

  return (
    <FilterToolbar
      label={t('products.filters.label')}
      hint={t('products.filters.hint')}
      chips={chips}
      onRemove={(key) => onChange({ [key]: NO_FILTERS[key as RemovableFilter] })}
      onClear={onClear}
    >
      <div className="filter-toolbar__search">
        <SearchField
          label={t('products.filters.q')}
          placeholder={t('products.filters.qPlaceholder')}
          value={filters.q}
          onCommit={(q) => onChange({ q })}
        />
      </div>

      <SearchField
        label={t('products.filters.sku')}
        placeholder={t('products.filters.skuPlaceholder')}
        value={filters.sku}
        onCommit={(sku) => onChange({ sku })}
      />

      <SelectField
        label={t('products.filters.type')}
        labelVisibility="hidden"
        value={filters.typeId === null ? '' : String(filters.typeId)}
        disabled={types.isPending}
        onChange={(event: ChangeEvent<HTMLSelectElement>) =>
          onChange({ typeId: event.target.value === '' ? null : Number(event.target.value) })
        }
      >
        <option value="">{t('products.filters.anyType')}</option>
        {(types.data ?? []).map((type) => (
          <option key={type.id} value={type.id}>
            {type.name}
          </option>
        ))}
      </SelectField>

      <SelectField
        label={t('products.filters.status')}
        labelVisibility="hidden"
        value={filters.status}
        onChange={(event: ChangeEvent<HTMLSelectElement>) =>
          onChange({ status: event.target.value as ProductStatusFilter })
        }
      >
        {STATUSES.map((status) => (
          <option key={status} value={status}>
            {t(`products.statusFilter.${status}`)}
          </option>
        ))}
      </SelectField>

      <SelectField
        label={t('products.filters.sort')}
        labelVisibility="hidden"
        value={filters.sort}
        onChange={(event: ChangeEvent<HTMLSelectElement>) =>
          onChange({ sort: event.target.value as AdminProductSort })
        }
      >
        {SORTS.map((sort) => (
          <option key={sort} value={sort}>
            {t(`products.sort.${sort}`)}
          </option>
        ))}
      </SelectField>
    </FilterToolbar>
  );
}
