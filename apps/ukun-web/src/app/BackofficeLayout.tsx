import { Suspense } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Outlet } from 'react-router-dom';
import { Container } from 'react-bootstrap';

import { RequireRole } from '../features/auth/RequireRole';
import { ACCOUNT_PATH } from '../features/auth/routes';
import { useMyEmployment } from '../features/backoffice/api/employment';
import { useAuth } from '../shared/hooks/useAuth';
import { useLocaleSync } from '../shared/hooks/useLocaleSync';
import { useRouteFocus } from '../shared/hooks/useRouteFocus';
import { LanguageSwitcher } from '../shared/ui/LanguageSwitcher';
import { LoadingState } from '../shared/ui/LoadingState';
import { BackofficeRail } from './BackofficeRail';
import { useRailExpanded } from './useRailExpanded';

/**
 * The frame of the back-office: a rail down the left, a slim bar across the top, and the screen.
 *
 * It is a third layout next to `AppLayout` and `AuthLayout` for the reason `AuthLayout` already
 * documents — what it needs is **subtraction**. An employee adjusting stock was being shown a
 * shopper's navigation: the catalogue menu, the product search box, the cart indicator and a foot
 * full of shop links, none of which is a thing to do at work. What replaces them is the rail, which
 * lists the two or three screens the account may actually open.
 *
 * Two greens, and they are not the same one on purpose: `$primary-hover` in the rail against
 * `$primary` in the bar. `_shell.scss` learned that the hard way with the footer — two adjacent
 * blocks of the identical green read as one mass with no edge between them.
 *
 * `useLocaleSync` like the other two shells. **No `useRestoreReturnTo`**: coming back from the
 * authorization server always lands on `/`, which belongs to `AppLayout`, so nobody ever returns to
 * a route of this one.
 *
 * **The employee role is required here and not route by route.** It used to be composed eight times
 * in `App.tsx`, once per screen; the shell is the honest place for it because it is the shell that
 * only makes sense for staff. It guards the `Outlet` rather than wrapping the whole layout so that
 * somebody without the role is refused **inside** the frame: the rail is still there, and with it the
 * way back to the shop. Refused outside it, the only way out was the browser's back button. It is not
 * the barrier either way — the gateway refuses what it protects and `ukun-api` checks the role again
 * on every request (`docs/08-identidad-y-seguridad.md` §4).
 */
export function BackofficeLayout() {
  const { t } = useTranslation(['backoffice', 'common']);
  const { user } = useAuth();
  /**
   * Where this account works, for the bar. It is the **query** and never `EmploymentBoundary`: the
   * boundary refuses to draw its screen until the store is known, and a shell that did that would
   * hold the whole back-office hostage to one request. Here the store is a nice-to-know — if it has
   * not arrived, or the account has no employee row behind it, the bar simply says one thing less.
   *
   * It costs no extra request on the four screens that already resolve it, because TanStack Query
   * dedupes them onto one key with `staleTime: Infinity`; on the three that did not —the index, the
   * catalogue and the product sheet— it is one request, answered once per session.
   */
  const employment = useMyEmployment();
  const { expanded, toggle } = useRailExpanded();
  useLocaleSync();
  useRouteFocus();

  return (
    // El ancho del rail lo publica el cascarón como custom property, porque hay una tercera cosa
    // que depende de él: la barra fija de acciones de la ficha de producto, que sin esto se metería
    // por debajo del rail.
    <div className={`bo-shell${expanded ? ' bo-shell--wide-rail' : ''}`}>
      {/* Worth more here than in the shop: the rail is four to six stops before the content, on every
          single screen of the back-office. */}
      <a href="#main-content" className="skip-link">
        {t('common:nav.skipToContent')}
      </a>

      <BackofficeRail expanded={expanded} onToggle={toggle} />

      <div className="bo-shell__body">
        {/* What is left of an account block once the rail carries both of its destinations: signing
            out is down there, and the shop is down there too. What is not in the rail is who you are
            and the language, so that is what the bar holds.

            The store goes **next to the email and in the same weight**, not on the far left where a
            title would sit. It is a fact about the session —where you work— and not the subject of
            the screen: the catalogue of `/admin/products` is global and belongs to no store
            (ADR-0020), so a store name with the presence of a heading over it would be claiming
            something false. Grouped with the email, it reads as the pair it is. */}
        {/* `<header>`, no un `<div>` suelto: el idioma, la tienda y la cuenta quedaban fuera del
            rail (que es el `<nav>`) y fuera del `<main>`, o sea fuera de todo landmark. Para quien
            navega por regiones eso es contenido que no existe. */}
        <header className="bo-topbar">
          <LanguageSwitcher />
          {employment.data !== undefined && (
            <p className="bo-topbar__store mb-0">
              {/* Named for a screen reader, which otherwise hears a store and an email side by side
                  with nothing saying which is which. On screen the pairing says it already. */}
              <span className="visually-hidden">{t('topbar.store')} </span>
              {employment.data.storeName}
            </p>
          )}
          {user !== undefined && (
            <Link to={ACCOUNT_PATH} className="bo-topbar__account">
              {user.email}
            </Link>
          )}
        </header>

        <main id="main-content" tabIndex={-1} className="bo-main">
          {/* The width is decided once, here, and not eight times: every back-office screen used to
              open with its own `<Container className="py-5">`. */}
          <Container fluid="xxl" className="py-4">
            <Suspense fallback={<LoadingState />}>
              <RequireRole role="EMPLOYEE">
                <Outlet />
              </RequireRole>
            </Suspense>
          </Container>
        </main>
      </div>
    </div>
  );
}
