import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router-dom';
import { Alert, Button } from 'react-bootstrap';

import { continueToAuthorizationServer, readReason } from '../../shared/auth/signIn';
import { Icon } from '../../shared/ui/Icon';
import { AuthCard } from './components/AuthCard';
import { RequireAnonymous } from './RequireAnonymous';
import { SIGN_UP_PATH } from './routes';

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
  const [params] = useSearchParams();

  const reason = readReason(params);

  return (
    <RequireAnonymous>
      <AuthCard
        title={t('account.signInPage.title')}
        lead={t('account.signInPage.lead')}
        notice={
          // Why this screen is being shown, when it was not asked for. `Alert` is announced on its
          // own (`role="alert"`), which is what the reader needs when the session died under them.
          reason !== undefined && (
            <Alert variant="warning" className="mb-4">
              {t(`account.signInPage.reason.${reason}`)}
            </Alert>
          )
        }
      >
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
        {/* El alta sí es una ruta de este router — la cuenta la crea `ukun-api` (docs/08 §3) —, así
            que es un enlace y no un botón que se lleva el navegador fuera como el de arriba. */}
        <Link to={SIGN_UP_PATH} className="btn btn-outline-primary w-100">
          {t('account.signUp')}
        </Link>
      </AuthCard>
    </RequireAnonymous>
  );
}
