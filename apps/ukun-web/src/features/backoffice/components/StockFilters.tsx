import type { FocusEvent, KeyboardEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Col, Row } from 'react-bootstrap';

import { TextField } from '../../../shared/ui/TextField';
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
 * There is no "apply" button, and there is no filtering per keystroke either — both fields commit
 * when you leave them or press Enter, which is what `docs/07-frontend-ux-ui.md` prescribes for a
 * typed filter: crossing the field is one request, not forty. What holds the answer is the URL
 * (`useStockParams`), so a filtered table is linkable and survives a reload.
 *
 * The controls are uncontrolled, keyed by the value in the URL. That is deliberate: the input's own
 * draft —what has been typed and not yet committed— is the one piece of state that has no business
 * in the address bar, and keying on the committed value is what makes "clear filters" empty the boxes
 * without a second copy of the value to keep in sync.
 */
export function StockFilters({ filters, onChange, onClear }: StockFiltersProps) {
  const { t } = useTranslation('backoffice');

  const commit = (patch: Partial<StockFilterInput>) => {
    const [[key, value]] = Object.entries(patch) as [[keyof StockFilterInput, string]];

    if (value !== filters[key]) {
      onChange(patch);
    }
  };

  const commitOnEnter = (event: KeyboardEvent<HTMLInputElement>, field: 'sku' | 'q') => {
    if (event.key === 'Enter') {
      event.preventDefault();
      commit({ [field]: event.currentTarget.value });
    }
  };

  return (
    <fieldset className="mb-4">
      <legend className="h6 text-body-secondary">{t('stock.filters.title')}</legend>

      <Row className="g-3 align-items-end">
        <Col xs={12} sm={4}>
          <TextField
            key={`sku-${filters.sku}`}
            label={t('stock.filters.sku')}
            hint={t('stock.filters.skuHint')}
            type="search"
            autoComplete="off"
            defaultValue={filters.sku}
            onBlur={(event: FocusEvent<HTMLInputElement>) => commit({ sku: event.target.value })}
            onKeyDown={(event: KeyboardEvent<HTMLInputElement>) => commitOnEnter(event, 'sku')}
          />
        </Col>
        <Col xs={12} sm={5}>
          <TextField
            key={`q-${filters.q}`}
            label={t('stock.filters.q')}
            hint={t('stock.filters.qHint')}
            type="search"
            autoComplete="off"
            defaultValue={filters.q}
            onBlur={(event: FocusEvent<HTMLInputElement>) => commit({ q: event.target.value })}
            onKeyDown={(event: KeyboardEvent<HTMLInputElement>) => commitOnEnter(event, 'q')}
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
