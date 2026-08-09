import { useTranslation } from 'react-i18next';
import { Button } from 'react-bootstrap';

import { Icon } from '../../../shared/ui/Icon';
import { formatMoney } from '../../../shared/format/money';
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
 */
export function ActiveFilters({ filters, types, onRemove, onClear }: ActiveFiltersProps) {
  const { t, i18n } = useTranslation('catalog');
  const locale = i18n.resolvedLanguage ?? 'es';

  const typeName = types.find((type) => type.id === filters.typeId)?.name;

  const chips: { key: keyof CatalogFilterInput; label: string }[] = [
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

  if (chips.length === 0) {
    return null;
  }

  return (
    <div role="group" aria-label={t('filters.active')} className="d-flex flex-wrap align-items-center gap-2 mb-3">
      {chips.map((chip) => (
        <Button
          key={chip.key}
          type="button"
          variant="light"
          size="sm"
          className="rounded-pill border d-inline-flex align-items-center gap-1"
          aria-label={t('filters.remove', { label: chip.label })}
          onClick={() => onRemove(chip.key)}
        >
          {chip.label}
          <Icon name="close" size="sm" />
        </Button>
      ))}
      <Button type="button" variant="link" size="sm" onClick={onClear}>
        {t('filters.clearAll')}
      </Button>
    </div>
  );
}
