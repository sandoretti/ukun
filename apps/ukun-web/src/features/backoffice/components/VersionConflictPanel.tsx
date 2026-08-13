import { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert, Button, Spinner } from 'react-bootstrap';

type VersionConflictPanelProps = {
  /** What the line holds now, straight from the re-read the conflict triggered. */
  serverQuantity: number;
  /** What this employee had typed and tried to save. */
  yourQuantity: number;
  /** A write or the re-read is still in flight: the figures below may not be the final ones yet. */
  isBusy: boolean;
  onKeepServer: () => void;
  onApplyMine: () => void;
};

/**
 * What two employees editing the same line at once see (ADR-0008).
 *
 * The screen does **not** swallow the `409` and it does **not** retry on its own — retrying with a
 * fresh version is precisely the silent overwrite optimistic locking exists to prevent. It shows both
 * figures and asks, which is the only honest thing to do when the answer depends on what happened in
 * the shop and not on what the software prefers.
 *
 * It deliberately does not use `errors:VERSION_CONFLICT` either. That sentence says "reload it", and
 * by the time this panel is on screen the reload has already happened: the mutation invalidated the
 * listing and the figure on the left came back with it.
 *
 * The heading takes focus on arrival and the panel announces itself. The dialog was open and its
 * contents changed underneath whoever was reading it — react-bootstrap keeps focus inside the dialog,
 * but nothing tells a screen reader that the question changed.
 */
export function VersionConflictPanel({
  serverQuantity,
  yourQuantity,
  isBusy,
  onKeepServer,
  onApplyMine,
}: VersionConflictPanelProps) {
  const { t } = useTranslation('backoffice');
  const heading = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    heading.current?.focus();
  }, []);

  return (
    <Alert variant="warning" className="mb-0" aria-live="polite">
      <h2 className="h6" ref={heading} tabIndex={-1}>
        {t('edit.conflict.title')}
      </h2>
      <p>{t('edit.conflict.body')}</p>

      <dl className="row mb-3">
        <dt className="col-7 fw-normal">{t('edit.conflict.server')}</dt>
        <dd className="col-5 text-end text-tabular-nums fw-semibold">{serverQuantity}</dd>
        <dt className="col-7 fw-normal">{t('edit.conflict.yours')}</dt>
        <dd className="col-5 text-end text-tabular-nums fw-semibold mb-0">{yourQuantity}</dd>
      </dl>

      {isBusy && (
        <p className="d-flex align-items-center gap-2 text-body-secondary">
          <Spinner animation="border" size="sm" />
          {t('edit.conflict.reloading')}
        </p>
      )}

      <div className="d-flex flex-wrap gap-2">
        <Button variant="outline-secondary" onClick={onKeepServer} disabled={isBusy}>
          {t('edit.conflict.keepServer', { quantity: serverQuantity })}
        </Button>
        <Button variant="warning" onClick={onApplyMine} disabled={isBusy}>
          {t('edit.conflict.applyMine', { quantity: yourQuantity })}
        </Button>
      </div>
    </Alert>
  );
}
