import { useTranslation } from 'react-i18next';
import { Button, Col, Row } from 'react-bootstrap';

import { CommittedTextField } from '../../../shared/ui/CommittedTextField';
import type { StockFilterInput } from '../api/stockKeys';
import { countActiveFilters } from '../useStockParams';

type StockFiltersProps = {
  filters: StockFilterInput;
  onChange: (patch: Partial<StockFilterInput>) => void;
  onClear: () => void;
};

/**
 * The two ways an employee looks for a product: the SKU they were given, and the name they remember.
 *
 * Both fields commit when you leave them or press Enter rather than filtering per keystroke — that
 * is `CommittedTextField`'s whole job, and `docs/07-frontend-ux-ui.md` is where the rule comes from.
 * What holds the answer is the URL (`useStockParams`), so a filtered table is linkable and survives
 * a reload.
 */
export function StockFilters({ filters, onChange, onClear }: StockFiltersProps) {
  const { t } = useTranslation('backoffice');

  return (
    <fieldset className="mb-4">
      <legend className="h6 text-body-secondary">{t('stock.filters.title')}</legend>

      <Row className="g-3 align-items-end">
        <Col xs={12} sm={4}>
          <CommittedTextField
            label={t('stock.filters.sku')}
            hint={t('stock.filters.skuHint')}
            value={filters.sku}
            onCommit={(sku) => onChange({ sku })}
          />
        </Col>
        <Col xs={12} sm={5}>
          <CommittedTextField
            label={t('stock.filters.q')}
            hint={t('stock.filters.qHint')}
            value={filters.q}
            onCommit={(q) => onChange({ q })}
          />
        </Col>
        <Col xs={12} sm={3}>
          {/* Only there when there is something to clear: a permanently disabled control is one more
              thing to read on a screen that has nothing filtered. */}
          {countActiveFilters(filters) > 0 && (
            <Button variant="link" className="link-secondary px-0 mb-3" onClick={onClear}>
              {t('stock.filters.clear')}
            </Button>
          )}
        </Col>
      </Row>
    </fieldset>
  );
}
