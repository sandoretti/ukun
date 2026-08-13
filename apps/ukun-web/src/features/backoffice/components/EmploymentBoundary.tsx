import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { toProblem } from '../../../shared/api/http';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { ErrorState } from '../../../shared/ui/ErrorState';
import { LoadingState } from '../../../shared/ui/LoadingState';
import { useMyEmployment, type Employment } from '../api/employment';

const FORBIDDEN = 403;

type EmploymentBoundaryProps = {
  children: (employment: Employment) => ReactNode;
};

/**
 * Resolves which store the employee works in before anything else on the screen runs, and hands it
 * down already known.
 *
 * **This is why no component in this feature takes an optional store.** The four stock routes carry
 * `{storeId}` in the path, and the temptation with a store that "might not be there yet" is a chain
 * of `enabled: storeId !== undefined` and non-null assertions spread over every hook. Resolving it
 * once, at the top, and rendering nothing until it is there costs one wait and removes the whole
 * class of problem.
 *
 * It is also the only place the store comes from. Nothing here reads a store identifier out of the
 * URL: `{storeId}` identifies a resource, it never authorises access to it, and the server resolves
 * ownership from `account_id` on every request (`docs/08-identidad-y-seguridad.md` §4).
 */
export function EmploymentBoundary({ children }: EmploymentBoundaryProps) {
  const { t } = useTranslation('backoffice');
  const query = useMyEmployment();

  if (query.data !== undefined) {
    return <>{children(query.data)}</>;
  }
  if (query.isPending) {
    return <LoadingState />;
  }
  // An account with no employee row is not a failure to report in red: it is a screen that will
  // never work for whoever is looking at it, and there is nothing for them to retry. The route guard
  // already turns away anyone without the role; this covers the account that has the role and no
  // profile behind it.
  if (toProblem(query.error).status === FORBIDDEN) {
    return <EmptyState icon="badge" message={t('stock.employment.denied')} />;
  }

  return <ErrorState error={query.error} />;
}
