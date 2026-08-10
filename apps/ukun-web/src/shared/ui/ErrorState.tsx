import { useTranslation } from 'react-i18next';
import { Alert } from 'react-bootstrap';

import { toProblem } from '../api/http';
import { Icon } from './Icon';

type ErrorStateProps = {
  /**
   * Whatever the query rejected with, unnarrowed. Normalising it is this component's job and not
   * the screen's: `toProblem` runs once, here, instead of twice at every call site to pull `code`
   * and `extensions` out of the same object.
   */
  error: unknown;
};

/**
 * How a failed read is painted. The message is the `code` of the `ProblemDetail` resolved through
 * `errors.json` (ADR-0019) with the problem's own extensions as interpolation parameters — the raw
 * `detail` is written for the operator and never reaches the screen.
 */
export function ErrorState({ error }: ErrorStateProps) {
  const { t } = useTranslation(['errors', 'common']);
  const problem = toProblem(error);

  return (
    <Alert variant="danger" className="d-flex align-items-center">
      <Icon name="error" className="me-2" />
      <div>{t(`errors:${problem.code}`, problem.extensions)}</div>
    </Alert>
  );
}
