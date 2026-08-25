import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Container } from 'react-bootstrap';

import type { Role } from '../../shared/api/session';
import { useAuth } from '../../shared/hooks/useAuth';
import { Icon } from '../../shared/ui/Icon';
import { LoadingState } from '../../shared/ui/LoadingState';

type RequireRoleProps = {
  role: Role;
  children: ReactNode;
};

/**
 * A route that only exists for an account with a given role — the back-office screens (RF-09), which
 * are any employee's and nobody else's.
 *
 * **It is not a barrier and it does not pretend to be one.** The gateway refuses what it protects and
 * `ukun-api` checks the role again on every request (`docs/08-identidad-y-seguridad.md` §4); what this
 * saves is a screen that paints half a table and then fills up with `403`s. Roles are read through
 * `useAuth().hasRole`, which is the one place in the app that reads them — never a list of privileged
 * routes copied around the components.
 *
 * It is a sibling of `RequireAuth` rather than a prop on it because the two denials end somewhere
 * else. No session is a detour: you are sent to sign in and brought back. The wrong role is a
 * destination: signing in again gives you the same roles, so there is nothing to send anybody to, and
 * saying so where the reader is standing is the only useful answer. Hiding those two behind an
 * optional prop would be hiding two behaviours behind a `?`.
 */
export function RequireRole({ role, children }: RequireRoleProps) {
  const { hasRole, isPending } = useAuth();
  const { t } = useTranslation('common');

  // The probe answering, which is not a state this route has anything to say about.
  if (isPending) {
    return <LoadingState />;
  }

  if (!hasRole(role)) {
    return (
      <Container className="py-5">
        <div className="col-lg-8 col-xl-6">
          <Icon name="lock" size="lg" className="mb-3 opacity-50" />
          <h1 className="h2 mb-2">{t('account.noAccess.title')}</h1>
          <p className="text-body-secondary mb-4">{t('account.noAccess.lead')}</p>
          <Link to="/">{t('account.backHome')}</Link>
        </div>
      </Container>
    );
  }

  return <>{children}</>;
}
