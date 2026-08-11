import { useTranslation } from 'react-i18next';
import { Button } from 'react-bootstrap';

import { ErrorState } from '../../../shared/ui/ErrorState';
import { Icon } from '../../../shared/ui/Icon';

/** What the last submit ended in, for the line that tells the reader so. */
export type Outcome = 'saved' | 'unchanged';

type ProfileFormActionsProps = {
  outcome: Outcome | null;
  /** A failure that belongs to the form as a whole, unnarrowed so `ErrorState` translates its `code`. */
  formError: unknown;
  isDirty: boolean;
  isSaving: boolean;
  onDiscard: () => void;
};

/**
 * The foot of the settings form: what editing this does not touch, how the last save went, and the
 * two things you can do about it.
 *
 * The notice about past orders lives here and not next to the address it talks about, because this is
 * the last thing read before pressing save — and what it answers ("does changing my address rewrite
 * where my old orders went?") is a question you ask yourself at exactly that moment. It is the legacy
 * defect #19, closed in the backend with immutable snapshots (ADR-0011) and said out loud here.
 *
 * Two live regions, each wrapped tight around what changes: a failure and an outcome are not the same
 * announcement, and a region that also contains the buttons would read them out again every time the
 * submit label changes.
 */
export function ProfileFormActions({ outcome, formError, isDirty, isSaving, onDiscard }: ProfileFormActionsProps) {
  const { t } = useTranslation('profile');

  return (
    <div className="settings-actions">
      <p className="text-body-secondary small mb-3">{t('ordersNotice')}</p>

      <div aria-live="polite">{formError !== null && <ErrorState error={formError} />}</div>

      <div className="d-flex flex-wrap align-items-center justify-content-between gap-3">
        {/* Presente aunque esté vacío: una región que aparece con el mensaje dentro no se anuncia. */}
        <p className="mb-0 d-flex align-items-center gap-2 small text-body-secondary" aria-live="polite">
          {outcome !== null && (
            <>
              {/* Guardado y «no había nada que cambiar» no son el mismo desenlace: solo el primero
                  lleva el icono de confirmación del mapa de `docs/07` §Iconografía. */}
              {outcome === 'saved' && <Icon name="check_circle" size="sm" />}
              {t(outcome)}
            </>
          )}
        </p>

        <div className="d-flex gap-2">
          {/* Sin cambios no hay nada que descartar, y un botón que no hace nada es peor que ninguno. */}
          <Button type="button" variant="outline-secondary" disabled={!isDirty} onClick={onDiscard}>
            {t('discard')}
          </Button>
          <Button type="submit" variant="primary" disabled={isSaving}>
            {isSaving ? t('submitting') : t('submit')}
          </Button>
        </div>
      </div>
    </div>
  );
}
