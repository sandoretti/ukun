import { Suspense } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Outlet } from 'react-router-dom';
import { Container, Navbar } from 'react-bootstrap';

import { useLocaleSync } from '../shared/hooks/useLocaleSync';
import { BrandMark } from '../shared/ui/BrandMark';
import { LanguageSwitcher } from '../shared/ui/LanguageSwitcher';
import { LoadingState } from '../shared/ui/LoadingState';

/**
 * The frame of the sign-in screen: brand, language, and nothing else.
 *
 * It is a second layout rather than a variant of `AppLayout` because what it needs is subtraction —
 * no menu, no search field, no account block, no foot. Signing in is a single task and everything
 * on screen that is not that task is a way of not finishing it. The brand still links home, which
 * is also the way out of a layout that has no navigation of its own.
 *
 * The bar keeps the green of the site: `LanguageSwitcher` is drawn for a dark ground
 * (`_shell.scss` — white border, white caption, the active option inverted), so putting it on white
 * would mean inventing a second skin for a control that already works.
 */
export function AuthLayout() {
  const { t } = useTranslation('common');
  // `<html lang>` follows the language here too — this screen can be the first one someone opens.
  useLocaleSync();
  // No `useRestoreReturnTo` on purpose: coming back from the authorization server always lands on
  // `/`, which belongs to `AppLayout`. Nobody ever returns to this route.

  return (
    <div className="auth-shell">
      {/* No skip link either: with two controls before the content there is no block to bypass. */}
      <Navbar bg="primary" data-bs-theme="dark" aria-label={t('nav.primary')}>
        <Container className="justify-content-between">
          <Navbar.Brand as={Link} to="/">
            <BrandMark />
          </Navbar.Brand>
          <LanguageSwitcher />
        </Container>
      </Navbar>

      <main id="main-content" className="auth-shell__main">
        <Suspense fallback={<LoadingState />}>
          <Outlet />
        </Suspense>
      </main>
    </div>
  );
}
