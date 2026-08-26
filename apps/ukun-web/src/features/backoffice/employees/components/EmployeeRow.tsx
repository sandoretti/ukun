import { useTranslation } from 'react-i18next';
import { Badge } from 'react-bootstrap';

import type { Employee } from '../api/employeeKeys';

type EmployeeRowProps = {
  employee: Employee;
};

/**
 * One member of the store's staff.
 *
 * **There is no actions column, and its absence is a decision rather than a gap** (RF-11): editing
 * and deleting an employee are out of scope for v1.0, because with identity in another service
 * deleting one means disabling their account in `ukun-auth` and removing their profile here — the
 * inverse of the alta saga, not a `DELETE` on a table. A disabled button or a dead bin icon would be
 * promising a screen that does not exist.
 *
 * The administrator flag is a badge and not a "yes"/"no" column: it is the exception in a store, so
 * what the reader scans for is the handful of rows that carry it (RF-12). An ordinary employee gets
 * the word anyway, visually hidden — an empty cell reads as "blank" to a screen reader, which is not
 * the same thing as "not an administrator".
 */
export function EmployeeRow({ employee }: EmployeeRowProps) {
  const { t } = useTranslation('backoffice');

  return (
    <tr>
      <td>{employee.name}</td>
      <td>{employee.lastName}</td>
      <td>{employee.email}</td>
      <td>{employee.jobTitle}</td>
      <td>
        {employee.isAdmin ? (
          <Badge bg="primary">{t('employees.table.admin')}</Badge>
        ) : (
          <span className="visually-hidden">{t('employees.table.notAdmin')}</span>
        )}
      </td>
    </tr>
  );
}
