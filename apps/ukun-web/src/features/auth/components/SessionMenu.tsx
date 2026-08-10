import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Button, Dropdown } from 'react-bootstrap';

import { useAuth } from '../../../shared/hooks/useAuth';
import { ComingSoonButton } from '../../../shared/ui/ComingSoon';
import { ACCOUNT_PATH } from '../routes';
import { SignOutForm } from './SignOutForm';

/**
 * The account end of the navigation bar: who you are, or the way in if you are nobody yet.
 *
 * Signing in is a **navigation to the gateway**, not a screen of this router: `oauth2Login` owns
 * `/oauth2/authorization/ukun` and the callback, and the SPA never sees a token (ADR-0003). That is
 * why the entry point is a `<button>` and not a `<Link>` — the destination is not a route.
 *
 * While the session probe is in flight this renders the anonymous block, deliberately: the wait is
 * a couple of milliseconds, and a spinner or a disabled button in the bar would flicker on every
 * single page load. Pressing it early costs nothing — someone who already has a session comes back
 * from the gateway without being asked for anything.
 */
export function SessionMenu() {
  const { t } = useTranslation('common');
  const { user, hasRole, signIn } = useAuth();

  if (user === undefined) {
    return (
      <>
        <Button type="button" variant="outline-light" size="sm" onClick={signIn}>
          {t('account.signIn')}
        </Button>
        {/* El alta es otra pantalla que todavía no existe: se sigue ofreciendo, deshabilitada. */}
        <ComingSoonButton label={t('account.signUp')} variant="light" />
      </>
    );
  }

  // Lo que decide esta entrada son los roles de `GET /api/me`, en un solo sitio. Ocultarla es UX:
  // la barrera está en el gateway y otra vez en ukun-api (docs/08 §4).
  const isStaff = hasRole('EMPLOYEE') || hasRole('ADMIN');

  return (
    <Dropdown align="end">
      {/* El correo es el nombre accesible del menú: es el dato que identifica la sesión, y
          repetirlo dentro sería decir dos veces lo mismo. */}
      <Dropdown.Toggle variant="outline-light" size="sm" id="session-menu">
        {user.email}
      </Dropdown.Toggle>

      <Dropdown.Menu>
        <Dropdown.Item as={Link} to={ACCOUNT_PATH}>
          {t('account.title')}
        </Dropdown.Item>

        {isStaff && (
          <Dropdown.Item
            as="button"
            type="button"
            disabled
            aria-label={t('nav.comingSoon', { label: t('account.backoffice') })}
          >
            {t('account.backoffice')}
          </Dropdown.Item>
        )}

        <Dropdown.Divider />

        {/* El formulario no es un `Dropdown.Item`: un item es un enlace o un botón suelto, y salir
            necesita un `<form>` entero con su campo oculto. Se le da el ancho del menú para que no
            parezca una acción de segunda. */}
        <div className="px-3 pb-2 d-grid">
          <SignOutForm variant="outline-primary" />
        </div>
      </Dropdown.Menu>
    </Dropdown>
  );
}
