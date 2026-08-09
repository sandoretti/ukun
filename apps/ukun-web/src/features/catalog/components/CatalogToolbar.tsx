import { useTranslation } from 'react-i18next';
import { Badge, Button, Form } from 'react-bootstrap';

import { Icon } from '../../../shared/ui/Icon';
import type { ProductSort } from '../api/productKeys';
import { SORT_OPTIONS } from '../useCatalogParams';

type CatalogToolbarProps = {
  /** `undefined` until the first page arrives; the count is not invented in the meantime. */
  totalElements?: number;
  sort: ProductSort;
  activeFilterCount: number;
  availabilityFailed: boolean;
  onSortChange: (sort: ProductSort) => void;
  onOpenFilters: () => void;
};

/**
 * The bar above the grid: how many results there are, how they are sorted, and — on a phone, where
 * the sidebar is an offcanvas — the button that opens the filters.
 */
export function CatalogToolbar({
  totalElements,
  sort,
  activeFilterCount,
  availabilityFailed,
  onSortChange,
  onOpenFilters,
}: CatalogToolbarProps) {
  const { t } = useTranslation('catalog');

  return (
    <div className="mb-3">
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-2">
        <div className="d-flex align-items-center gap-3">
          <Button
            type="button"
            variant="outline-secondary"
            size="sm"
            className="d-lg-none d-inline-flex align-items-center gap-1"
            onClick={onOpenFilters}
          >
            <Icon name="tune" size="sm" />
            {t('filters.open')}
            {activeFilterCount > 0 && (
              <>
                <Badge bg="primary" aria-hidden="true">
                  {activeFilterCount}
                </Badge>
                {/* The badge is a digit: what names the button for a screen reader is this. */}
                <span className="visually-hidden">{t('filters.activeCount', { count: activeFilterCount })}</span>
              </>
            )}
          </Button>
          {/* The count moves under the user's feet as they filter, so it is announced. */}
          <p className="mb-0 text-body-secondary" aria-live="polite">
            {totalElements === undefined ? '' : t('results', { count: totalElements })}
          </p>
        </div>

        <Form.Group controlId="catalog-sort" className="d-flex align-items-center gap-2">
          <Form.Label className="mb-0 small text-body-secondary flex-shrink-0">{t('sort.label')}</Form.Label>
          {/* `w-auto`: dentro de un flex, `.form-select` se estira al 100% y el ancho lo acaba
              fijando el hueco que queda, no el texto — que es lo que corta "Precio: de menor a
              mayor" en ambos idiomas. */}
          <Form.Select
            size="sm"
            className="w-auto"
            value={sort}
            onChange={(event) => onSortChange(event.target.value as ProductSort)}
          >
            {SORT_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {t(`sort.${option}`)}
              </option>
            ))}
          </Form.Select>
        </Form.Group>
      </div>

      {availabilityFailed && (
        // Availability is secondary information: losing it hides the dots, it does not hide the
        // catalog, and it does not deserve a full-width banner either. What it never does is
        // degrade to "unknown" (RF-01).
        <p role="status" className="small text-body-secondary d-flex align-items-center gap-1 mt-2 mb-0">
          <Icon name="warning" size="sm" />
          {t('availability.unknown')}
        </p>
      )}
    </div>
  );
}
