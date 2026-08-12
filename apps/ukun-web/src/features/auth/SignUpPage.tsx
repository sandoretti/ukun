import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert, Button } from 'react-bootstrap';

import { continueToAuthorizationServer } from '../../shared/auth/signIn';
import { Icon } from '../../shared/ui/Icon';
import { AuthCard } from './components/AuthCard';
import { RequireAnonymous } from './RequireAnonymous';
import { SignUpForm } from './components/SignUpForm';
import type { SignupOutcome } from './api/queries';

/**
 * Where a customer is created (RF-06).
 *
 * It shares the bare frame of the sign-in screen —brand, title, way back and nothing else— because
 * it is the same single task seen from the other end, and everything on screen that is not that task
 * is a way of not finishing it. Wider than that one, though: this is a four-group form and not a
 * single button.
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
  const [pendingActivation, setPendingActivation] = useState(false);

  function handleSignedUp(outcome: SignupOutcome) {
    if (outcome === 'activationPending') {
      setPendingActivation(true);
      return;
    }

    continueToAuthorizationServer();
  }

  return (
    <RequireAnonymous>
      <AuthCard title={t('title')} lead={t('lead')} measure="form">
        {pendingActivation ? (
          <>
            {/* `role="alert"` de `Alert`: es el desenlace de lo que se acaba de enviar, y quien no ve
                la pantalla tiene que enterarse sin ir a buscarlo. */}
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
      </AuthCard>
    </RequireAnonymous>
  );
}
