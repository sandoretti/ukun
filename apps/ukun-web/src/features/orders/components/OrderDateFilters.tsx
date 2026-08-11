import type { ChangeEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Col, Row } from 'react-bootstrap';

import { TextField } from '../../../shared/ui/TextField';
import type { OrderFilterInput } from '../api/orderKeys';

type OrderDateFiltersProps = {
  filters: OrderFilterInput;
  onChange: (patch: Partial<OrderFilterInput>) => void;
  onClear: () => void;
};

/**
 * The date range of the history. Two native date controls, applied as they change: there is no
 * "apply" button because there is nothing to batch, and the URL is what holds the answer
 * (`useOrderParams`) — so a filtered range is linkable and survives a reload.
 *
 * Both ends are inclusive in the contract, and both are optional: only `from` means "from then on".
 * The pair is a `fieldset` rather than two loose fields, because "from" and "to" mean nothing apart
 * — the same rule the profile's long form follows.
 */
export function OrderDateFilters({ filters, onChange, onClear }: OrderDateFiltersProps) {
  const { t } = useTranslation('orders');

  const hasFilters = filters.from !== '' || filters.to !== '';

  return (
    <fieldset className="order-filters mb-4">
      <legend className="h6 text-body-secondary">{t('filters.title')}</legend>

      <Row className="g-3 align-items-end">
        <Col xs={12} sm={4}>
          <TextField
            label={t('filters.from')}
            type="date"
            value={filters.from}
            max={filters.to || undefined}
            onChange={(event: ChangeEvent<HTMLInputElement>) => onChange({ from: event.target.value })}
          />
        </Col>
        <Col xs={12} sm={4}>
          <TextField
            label={t('filters.to')}
            type="date"
            value={filters.to}
            min={filters.from || undefined}
            onChange={(event: ChangeEvent<HTMLInputElement>) => onChange({ to: event.target.value })}
          />
        </Col>
        <Col xs={12} sm={4}>
          {/* Only there when there is something to clear: a permanently disabled control is one more
              thing to read on a screen that has nothing filtered. */}
          {hasFilters && (
            <Button variant="link" className="link-secondary px-0 mb-3" onClick={onClear}>
              {t('filters.clear')}
            </Button>
          )}
        </Col>
      </Row>
    </fieldset>
  );
}
