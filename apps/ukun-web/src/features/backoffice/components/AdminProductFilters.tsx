import type { ChangeEvent, FocusEvent, KeyboardEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Col, Row } from 'react-bootstrap';

import { useProductTypes } from '../../../shared/api/productTypes';
import { SelectField } from '../../../shared/ui/SelectField';
import { TextField } from '../../../shared/ui/TextField';
import type {
  AdminProductFilterInput,
  AdminProductSort,
  ProductStatusFilter,
} from '../api/adminProductKeys';
import { countActiveFilters } from '../useAdminProductParams';

const STATUSES: ProductStatusFilter[] = ['ACTIVE', 'DISCONTINUED', 'ALL'];

const SORTS: AdminProductSort[] = ['NAME_ASC', 'NAME_DESC', 'PRICE_ASC', 'PRICE_DESC', 'UPDATED_DESC'];

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
 * The typed fields commit when you leave them or press Enter, never per keystroke: crossing a field
 * is one request, not forty (`docs/07-frontend-ux-ui.md`). The closed lists commit on change, because
 * choosing from a list *is* the decision. What holds the answer is the URL, so a filtered table is
 * linkable and survives a reload.
 *
 * The typed controls are uncontrolled and keyed by the value in the URL, like the stock filters: the
 * input's own draft is the one piece of state that has no business in the address bar, and keying on
 * the committed value is what makes "clear" empty the boxes with no second copy to keep in sync.
 */
export function AdminProductFilters({ filters, onChange, onClear }: AdminProductFiltersProps) {
  const { t } = useTranslation('backoffice');
  const types = useProductTypes();

  const commitTerm = (field: 'q' | 'sku', value: string) => {
    if (value !== filters[field]) {
      onChange({ [field]: value });
    }
  };

  const commitOnEnter = (event: KeyboardEvent<HTMLInputElement>, field: 'q' | 'sku') => {
    if (event.key === 'Enter') {
      event.preventDefault();
      commitTerm(field, event.currentTarget.value);
    }
  };

  return (
    <fieldset className="mb-4">
      <legend className="h6 text-body-secondary">{t('products.filters.title')}</legend>

      <Row className="g-3 align-items-start">
        <Col xs={12} md={4}>
          <TextField
            key={`q-${filters.q}`}
            label={t('products.filters.q')}
            hint={t('products.filters.qHint')}
            type="search"
            autoComplete="off"
            defaultValue={filters.q}
            onBlur={(event: FocusEvent<HTMLInputElement>) => commitTerm('q', event.target.value)}
            onKeyDown={(event: KeyboardEvent<HTMLInputElement>) => commitOnEnter(event, 'q')}
          />
        </Col>
        <Col xs={12} md={3}>
          <TextField
            key={`sku-${filters.sku}`}
            label={t('products.filters.sku')}
            hint={t('products.filters.skuHint')}
            type="search"
            autoComplete="off"
            defaultValue={filters.sku}
            onBlur={(event: FocusEvent<HTMLInputElement>) => commitTerm('sku', event.target.value)}
            onKeyDown={(event: KeyboardEvent<HTMLInputElement>) => commitOnEnter(event, 'sku')}
          />
        </Col>
        <Col xs={12} md={5}>
          <SelectField
            label={t('products.filters.type')}
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
        </Col>
      </Row>

      <Row className="g-3 align-items-end">
        <Col xs={12} sm={5} md={4}>
          <SelectField
            label={t('products.filters.status')}
            hint={t('products.filters.statusHint')}
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
        </Col>
        <Col xs={12} sm={5} md={4}>
          <SelectField
            label={t('products.filters.sort')}
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
        </Col>
        <Col xs={12} sm={2} md={4}>
          {/* Only there when there is something to clear: a permanently disabled control is one more
              thing to read on a screen with nothing filtered. */}
          {countActiveFilters(filters) > 0 && (
            <Button variant="link" className="link-secondary px-0 mb-3" onClick={onClear}>
              {t('products.filters.clear')}
            </Button>
          )}
        </Col>
      </Row>
    </fieldset>
  );
}
