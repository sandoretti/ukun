import type { ChangeEvent, FocusEvent, KeyboardEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Col, Row } from 'react-bootstrap';

import { SelectField } from '../../../../shared/ui/SelectField';
import { TextField } from '../../../../shared/ui/TextField';
import type { EmployeeFilterInput } from '../api/employeeKeys';
import { countActiveFilters } from '../useEmployeeParams';

type EmployeeFiltersProps = {
  filters: EmployeeFilterInput;
  onChange: (patch: Partial<EmployeeFilterInput>) => void;
  onClear: () => void;
};

/**
 * The three values of the administrator filter, each with the key it is labelled by. Written out
 * rather than composed into `...adminOptions.${value}`: a key built at runtime is a key the parity
 * test cannot see, and a missing translation would only show up on screen.
 */
const ADMIN_OPTIONS = [
  { value: '', labelKey: 'employees.filters.adminOptions.all' },
  { value: 'true', labelKey: 'employees.filters.adminOptions.admins' },
  { value: 'false', labelKey: 'employees.filters.adminOptions.staff' },
] as const;

function toIsAdmin(value: string): boolean | null {
  return value === '' ? null : value === 'true';
}

/**
 * The two ways an admin narrows down their staff: the name —or surname, or email— they remember, and
 * whether they are looking for the administrators.
 *
 * There is no "apply" button, and there is no filtering per keystroke either: the text field commits
 * when you leave it or press Enter, which is what `docs/07-frontend-ux-ui.md` prescribes for a typed
 * filter — crossing the field is one request, not forty. The select commits on change, because
 * choosing from a closed list of three is already the decision.
 *
 * **The administrator filter is three states and not a checkbox**: not filtering, only
 * administrators, and only the employees who are not one. A checkbox could not ask for the third.
 *
 * The text control is uncontrolled, keyed by the value in the URL. That is deliberate: the input's
 * own draft —what has been typed and not yet committed— is the one piece of state that has no
 * business in the address bar, and keying on the committed value is what makes "clear filters" empty
 * the box without a second copy of the value to keep in sync.
 */
export function EmployeeFilters({ filters, onChange, onClear }: EmployeeFiltersProps) {
  const { t } = useTranslation('backoffice');

  const commitTerm = (value: string) => {
    if (value !== filters.q) {
      onChange({ q: value });
    }
  };

  return (
    <fieldset className="mb-4">
      <legend className="h6 text-body-secondary">{t('employees.filters.title')}</legend>

      <Row className="g-3 align-items-end">
        <Col xs={12} sm={5}>
          <TextField
            key={`q-${filters.q}`}
            label={t('employees.filters.q')}
            hint={t('employees.filters.qHint')}
            type="search"
            autoComplete="off"
            defaultValue={filters.q}
            onBlur={(event: FocusEvent<HTMLInputElement>) => commitTerm(event.target.value)}
            onKeyDown={(event: KeyboardEvent<HTMLInputElement>) => {
              if (event.key === 'Enter') {
                event.preventDefault();
                commitTerm(event.currentTarget.value);
              }
            }}
          />
        </Col>
        <Col xs={12} sm={4}>
          <SelectField
            label={t('employees.filters.isAdmin')}
            value={filters.isAdmin === null ? '' : String(filters.isAdmin)}
            onChange={(event: ChangeEvent<HTMLSelectElement>) =>
              onChange({ isAdmin: toIsAdmin(event.target.value) })
            }
          >
            {ADMIN_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {t(option.labelKey)}
              </option>
            ))}
          </SelectField>
        </Col>
        <Col xs={12} sm={3}>
          {/* Only there when there is something to clear: a permanently disabled control is one more
              thing to read on a screen that has nothing filtered. */}
          {countActiveFilters(filters) > 0 && (
            <Button variant="link" className="link-secondary px-0 mb-3" onClick={onClear}>
              {t('employees.filters.clear')}
            </Button>
          )}
        </Col>
      </Row>
    </fieldset>
  );
}
