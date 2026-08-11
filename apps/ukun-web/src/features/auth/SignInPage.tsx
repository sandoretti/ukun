import { useTranslation } from 'react-i18next';
import { Link, Navigate, useSearchParams } from 'react-router-dom';
import { Alert, Button } from 'react-bootstrap';

import { continueToAuthorizationServer, readReason } from '../../shared/auth/signIn';
import { useAuth } from '../../shared/hooks/useAuth';
import { ComingSoonButton } from '../../shared/ui/ComingSoon';
import { Icon } from '../../shared/ui/Icon';
import { LoadingState } from '../../shared/ui/LoadingState';

/**
 * The way in, as a screen of its own: one card, centred, with a single action.
 *
 * What it deliberately does **not** have is a password field. The credentials are typed at
 * `ukun-auth` and the SPA never sees them any more than it sees the token (ADR-0003), so the
 * primary action leaves the site — and the line under it says so before the button is pressed
 * rather than after. A form here would be a screen that lies about where the password goes.
 *
 * Its reason for existing over the bare navigation it replaces: something has to explain *why* the
 * browser is here when it was thrown out of somewhere, and something has to be linkable so that
 * "sign in" is a place and not an event.
 */
export default function SignInPage() {
  const { t } = useTranslation('common');
  const { isAuthenticated, isPending } = useAuth();
  const [params] = useSearchParams();

  const reason = readReason(params);

  if (isPending) {
    return <LoadingState />;
  }

  // Already signed in, so there is nothing to offer. It goes home rather than restoring the
  // destination itself: `AppLayout` is where `useRestoreReturnTo` lives, and it is the very same
  // landing the trip back from the authorization server takes. One mechanism, not two.
  if (isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  return (
    <div className="auth-card">
      {/* Why this screen is being shown, when it was not asked for. `Alert` is announced on its own
          (`role="alert"`), which is what the reader needs when the session died under them. */}
      {reason !== undefined && (
        <Alert variant="warning" className="mb-4">
          {t(`account.signInPage.reason.${reason}`)}
        </Alert>
      )}

      {/* La marca en tipografía, no el logotipo: el que existe es blanco y sobre la tarjeta no se
          vería. Es el mismo recurso que ya usa la portada (`.hero__eyebrow`). */}
      <p className="auth-card__brand">{t('title')}</p>

      {/* Único `<h1>` de la página — la barra de este layout no lleva ninguno. */}
      <h1 className="h3 mb-2">{t('account.signInPage.title')}</h1>
      <p className="text-body-secondary mb-4">{t('account.signInPage.lead')}</p>

      <Button
        type="button"
        variant="primary"
        size="lg"
        className="w-100 d-inline-flex align-items-center justify-content-center gap-2"
        onClick={continueToAuthorizationServer}
      >
        <Icon name="arrow_forward" size="sm" />
        {t('account.signInPage.continue')}
      </Button>

      {/* Un botón que se lleva el navegador fuera del sitio avisa antes, no después. */}
      <p className="form-text mt-3 mb-0">{t('account.signInPage.hint')}</p>

      <hr className="auth-card__divider" />

      <p className="text-body-secondary small mb-2">{t('account.signInPage.noAccount')}</p>
      <ComingSoonButton label={t('account.signUp')} variant="outline-primary" />

      {/* La salida: este layout no tiene navegación, y una pantalla sin vuelta atrás es una trampa
          para quien llegó aquí por un 401 sin pedirlo. */}
      <p className="mt-4 mb-0">
        <Link to="/" className="small">
          {t('account.signInPage.backHome')}
        </Link>
      </p>
    </div>
  );
}
