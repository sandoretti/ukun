import { useTranslation } from 'react-i18next';
import { Spinner } from 'react-bootstrap';

/** A read still in flight. Announced as a `status`, so the wait is not silent for a screen reader. */
export function LoadingState() {
  const { t } = useTranslation('common');

  return (
    <div
      className="d-flex justify-content-center align-items-center p-5"
      role="status"
      aria-label={t('loading')}
    >
      <Spinner animation="border" variant="primary" />
      <span className="visually-hidden">{t('loading')}</span>
    </div>
  );
}
