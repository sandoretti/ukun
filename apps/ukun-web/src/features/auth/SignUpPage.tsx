import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Navigate } from 'react-router-dom';
import { Alert, Button } from 'react-bootstrap';

import { continueToAuthorizationServer } from '../../shared/auth/signIn';
import { useAuth } from '../../shared/hooks/useAuth';
import { Icon } from '../../shared/ui/Icon';
import { LoadingState } from '../../shared/ui/LoadingState';
import { SignUpForm } from './components/SignUpForm';
import type { SignupOutcome } from './api/queries';

/**
 * Where a customer is created (RF-06).
 *
 * It shares the bare frame of the sign-in screen —brand, language, nothing else— because it is the
 * same single task seen from the other end, and everything on screen that is not that task is a way
 * of not finishing it. Wider than that one, though: this is a four-group form and not a single
 * button.
 *
 * **The two answers of the saga are both successes and are not the same news** (`docs/08` §3):
 *
 * - `201` — the account is active, so the browser leaves for the authorization server straight away.
 *   That is the contract's own "next step is the login", and an interstitial saying "you signed up,
 *   press here" would be a click between the intention and the password field.
 * - `202` — the customer exists but the activation was not confirmed. Signing in may not work yet, so
 *   this one **is** said out loud, with the way in offered rather than taken. Repeating the signup
 *   finishes the saga, which is why the text says to try again if the login refuses.
 */
export default function SignUpPage() {
  const { t } = useTranslation('signup');
  const { isAuthenticated, isPending } = useAuth();
  const [pendingActivation, setPendingActivation] = useState(false);

  function handleSignedUp(outcome: SignupOutcome) {
    if (outcome === 'activationPending') {
      setPendingActivation(true);
      return;
    }

    continueToAuthorizationServer();
  }

  if (isPending) {
    return <LoadingState />;
  }

  // Already signed in: there is nobody to create. It goes home rather than showing a form that would
  // only ever answer "that email is taken".
  if (isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  return (
    <div className="auth-card auth-card--wide">
      {/* La marca en tipografía, no el logotipo: el que existe es blanco y sobre la tarjeta no se
          vería. Mismo recurso que la pantalla de entrada. */}
      <p className="auth-card__brand">{t('brand')}</p>

      <h1 className="h3 mb-2">{t('title')}</h1>
      <p className="text-body-secondary mb-4">{t('lead')}</p>

      {pendingActivation ? (
        <>
          {/* `role="alert"` de `Alert`: es el desenlace de lo que se acaba de enviar, y quien no ve la
              pantalla tiene que enterarse sin ir a buscarlo. */}
          <Alert variant="success">
            <p className="mb-1 fw-semibold">{t('pending.title')}</p>
            <p className="mb-0">{t('pending.body')}</p>
          </Alert>

          <Button
            type="button"
            variant="primary"
            size="lg"
            className="w-100 d-inline-flex align-items-center justify-content-center gap-2"
            onClick={continueToAuthorizationServer}
          >
            <Icon name="arrow_forward" size="sm" />
            {t('pending.continue')}
          </Button>
        </>
      ) : (
        <SignUpForm onSignedUp={handleSignedUp} />
      )}

      {/* La salida: este marco no tiene navegación, y una pantalla sin vuelta atrás es una trampa. */}
      <p className="mt-4 mb-0">
        <Link to="/" className="small">
          {t('backHome')}
        </Link>
      </p>
    </div>
  );
}
