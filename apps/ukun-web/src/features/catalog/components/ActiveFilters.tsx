import { useTranslation } from 'react-i18next';

import { ActiveFilterChips, type FilterChip } from '../../../shared/ui/ActiveFilterChips';
import { formatMoney } from '../../../shared/format/money';
import { useLocale } from '../../../shared/hooks/useLocale';
import type { ProductType } from '../../../shared/api/productTypes';
import type { CatalogFilterInput } from '../api/productKeys';

type ActiveFiltersProps = {
  filters: CatalogFilterInput;
  types: ProductType[];
  onRemove: (key: keyof CatalogFilterInput) => void;
  onClear: () => void;
};

/**
 * What is filtered right now, one removable chip each. It is what makes the filters honest when
 * they are not all on screen: the name is typed in the navigation bar and the rest hides inside the
 * offcanvas on a phone, so without this row nothing would say why the listing is short.
 *
 * The chips are drawn by `shared/ui/ActiveFilterChips`, which the back-office listings use too. What
 * stays here is the only part that is the catalogue's: which filters there are, and how each one is
 * put into words — a type shows its name and a price shows its currency.
 */
export function ActiveFilters({ filters, types, onRemove, onClear }: ActiveFiltersProps) {
  const { t } = useTranslation('catalog');
  const locale = useLocale();

  const typeName = types.find((type) => type.id === filters.typeId)?.name;

  const chips: FilterChip[] = [
    ...(filters.name ? [{ key: 'name' as const, label: t('chips.name', { value: filters.name }) }] : []),
    // The type shows its name, never its identifier — and only once the list of types has arrived.
    ...(typeName !== undefined ? [{ key: 'typeId' as const, label: t('chips.type', { value: typeName }) }] : []),
    ...(filters.priceMin
      ? [{ key: 'priceMin' as const, label: t('chips.priceMin', { value: formatMoney(filters.priceMin, locale) }) }]
      : []),
    ...(filters.priceMax
      ? [{ key: 'priceMax' as const, label: t('chips.priceMax', { value: formatMoney(filters.priceMax, locale) }) }]
      : []),
  ];

  return (
    <div className="mb-3">
      <ActiveFilterChips
        chips={chips}
        onRemove={(key) => onRemove(key as keyof CatalogFilterInput)}
        onClear={onClear}
      />
    </div>
  );
}
