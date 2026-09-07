import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, NavLink } from 'react-router-dom';
import { Container, Nav, Navbar } from 'react-bootstrap';

import { ABOUT_PATH } from '../routes';
import { BrandMark } from './BrandMark';
import { LanguageSwitcher } from './LanguageSwitcher';
import { ProductSearchBox } from './ProductSearchBox';

/**
 * The entries of the navigation bar, in order. Keeping it as data is what stops the bar from
 * becoming three near-identical blocks of JSX with three chances to drift.
 *
 * **Every entry is a link now, and the shape of this list is the proof.** It used to allow a missing
 * `to`, meaning "the screen does not exist yet": that entry was rendered as a disabled button rather
 * than an `<a>`, because a link to a blank screen is worse than no link. Two entries lived there.
 * `news` went for good — no requirement of `docs/02-requisitos.md` asked for it and no item of
 * `docs/04-roadmap.md` planned it, so it was holding a slot in the bar on every route and making a
 * screen reader announce "News, coming soon" on all of them. `about` went the other way and became a
 * screen. With none left, `ComingSoon` had no callers and was deleted; if a future entry needs that
 * treatment again, it comes back with the screen it is waiting for, not before.
 */
const NAV_ITEMS = [
  { key: 'catalog', to: '/products' },
  { key: 'stores', to: '/stores' },
  { key: 'about', to: ABOUT_PATH },
] as const satisfies readonly { key: string; to: string }[];

type SiteHeaderProps = {
  /**
   * What closes the bar, after the language switcher: the account block. It arrives composed from
   * `AppLayout` instead of being imported here, because that block belongs to `features/auth/` and
   * this file is `shared/ui/` — the shell does not reach down into a feature. Composition also keeps
   * the bar free of the session: it knows nothing about who is looking at it.
   */
  children?: ReactNode;
};

/**
 * The navigation bar of every route, mounted once from `AppLayout`. It lives in `shared/ui/` next to
 * `SiteFooter` and for the same reason: it is part of the shell, not of any one feature.
 */
export function SiteHeader({ children }: SiteHeaderProps) {
  const { t } = useTranslation('common');

  return (
    // La barra lleva el verde de marca con texto blanco (6.02:1, AA — docs/07-frontend-ux-ui.md).
    // `data-bs-theme="dark"` es lo que hace que Bootstrap 5.3 aclare marca, enlaces y toggler: sin
    // él la barra queda casi blanca sobre blanco y no se distingue del contenido.
    // La página tiene dos landmarks de navegación —esta barra y el bloque «Explora» del pie—, así
    // que cada uno necesita su propio nombre para que un lector de pantalla los distinga al
    // listarlos.
    <Navbar bg="primary" data-bs-theme="dark" expand="lg" sticky="top" aria-label={t('nav.primary')}>
      <Container>
        <Navbar.Brand as={Link} to="/">
          <BrandMark />
        </Navbar.Brand>
        <Navbar.Toggle aria-label={t('nav.toggle')} />
        <Navbar.Collapse>
          <Nav>
            {NAV_ITEMS.map((item) => (
              <Nav.Link key={item.key} as={NavLink} to={item.to}>
                {t(`nav.${item.key}`)}
              </Nav.Link>
            ))}
          </Nav>
          {/* El buscador es la acción principal de una tienda, así que vive en la barra y no dentro
              del formulario de filtros. Los controles vuelven al tema claro: un campo oscuro sobre
              el verde de marca es ilegible, y el tema de la barra se hereda si no se corta aquí. */}
          <div data-bs-theme="light" className="flex-grow-1 my-2 my-lg-0 mx-lg-4">
            <ProductSearchBox />
          </div>
          {/* Idioma y cuenta cierran la barra en ese orden, que es también el de tabulación:
              primero lo que cambia cómo se lee la página, después lo que lleva a la sesión. */}
          <div className="navbar-tail d-flex align-items-center gap-2">
            <LanguageSwitcher />
            {children}
          </div>
        </Navbar.Collapse>
      </Container>
    </Navbar>
  );
}
