import type { ChangeEvent } from 'react';
import { useTranslation } from 'react-i18next';

import type { FilterChip } from '../../../../shared/ui/ActiveFilterChips';
import { FilterToolbar } from '../../../../shared/ui/FilterToolbar';
import { SearchField } from '../../../../shared/ui/SearchField';
import { SelectField } from '../../../../shared/ui/SelectField';
import type { EmployeeFilterInput } from '../api/employeeKeys';
import { NO_FILTERS } from '../useEmployeeParams';

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
 * The search commits when you leave it or press Enter, never per keystroke; the select commits on
 * change, because choosing from a closed list of three is already the decision.
 *
 * **The administrator filter is three states and not a checkbox**: not filtering, only
 * administrators, and only the employees who are not one. A checkbox could not ask for the third.
 */
export function EmployeeFilters({ filters, onChange, onClear }: EmployeeFiltersProps) {
  const { t } = useTranslation('backoffice');

  /**
   * What is filtered right now, in words. The administrator one says which side it asks for rather
   * than «Administrador: false», which is the filter's shape and not its meaning.
   */
  const chips: FilterChip[] = [
    ...(filters.q ? [{ key: 'q', label: t('employees.chips.q', { value: filters.q }) }] : []),
    ...(filters.isAdmin === null
      ? []
      : [
          {
            key: 'isAdmin',
            label: t(filters.isAdmin ? 'employees.chips.admins' : 'employees.chips.staff'),
          },
        ]),
  ];

  return (
    <FilterToolbar
      label={t('employees.filters.label')}
      hint={t('employees.filters.hint')}
      chips={chips}
      onRemove={(key) => onChange({ [key]: NO_FILTERS[key as 'q' | 'isAdmin'] })}
      onClear={onClear}
    >
      <div className="filter-toolbar__search">
        <SearchField
          label={t('employees.filters.q')}
          placeholder={t('employees.filters.qPlaceholder')}
          value={filters.q}
          onCommit={(q) => onChange({ q })}
        />
      </div>

      <SelectField
        label={t('employees.filters.isAdmin')}
        labelVisibility="hidden"
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
    </FilterToolbar>
  );
}
