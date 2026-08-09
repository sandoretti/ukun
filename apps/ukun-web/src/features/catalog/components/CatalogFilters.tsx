import { useTranslation } from 'react-i18next';
import { Form } from 'react-bootstrap';

import type { CatalogFilterInput, ProductType } from '../api/productKeys';
import { PriceRange } from './PriceRange';

type CatalogFiltersProps = {
  filters: CatalogFilterInput;
  types: ProductType[];
  /** End of the price track; `undefined` while the catalog has not said how far it goes. */
  maxPrice?: number;
  onChange: (patch: Partial<CatalogFilterInput>) => void;
};

/**
 * The filter sidebar: one section per criterion, separated by a rule, and every one of them applies
 * the moment it is used — a listing that needs a second click to obey is the pattern this replaces.
 *
 * There is no "online only" filter on purpose: availability is a separate read (ADR-0017 §3) and
 * filtering it in the client would leave the pagination lying about how many products there are.
 * There are no per-type counts either: the public `ProductType` carries none (that is
 * `AdminProductType`), and counting what is on the current page would count the wrong thing.
 */
export function CatalogFilters({ filters, types, maxPrice, onChange }: CatalogFiltersProps) {
  const { t } = useTranslation('catalog');

  return (
    <div>
      <h2 className="h6 d-none d-lg-block">{t('filters.title')}</h2>

      <fieldset className="filter-section">
        <legend className="h6 fw-normal text-body-secondary">{t('filters.type')}</legend>
        <Form.Check
          type="radio"
          id="catalog-type-all"
          name="typeId"
          label={t('filters.allTypes')}
          checked={filters.typeId === null}
          onChange={() => onChange({ typeId: null })}
        />
        {types.map((type) => (
          <Form.Check
            key={type.id}
            type="radio"
            id={`catalog-type-${type.id}`}
            name="typeId"
            label={type.name}
            checked={filters.typeId === type.id}
            onChange={() => onChange({ typeId: type.id })}
          />
        ))}
      </fieldset>

      {maxPrice !== undefined && (
        <fieldset className="filter-section">
          <legend className="h6 fw-normal text-body-secondary">{t('filters.price')}</legend>
          <PriceRange
            priceMin={filters.priceMin}
            priceMax={filters.priceMax}
            ceiling={maxPrice}
            onCommit={onChange}
          />
        </fieldset>
      )}
    </div>
  );
}
