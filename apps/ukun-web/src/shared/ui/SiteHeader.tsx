import { useTranslation } from 'react-i18next';
import { Link, NavLink } from 'react-router-dom';
import { Container, Nav, Navbar } from 'react-bootstrap';

import { ComingSoonButton, ComingSoonNavItem } from './ComingSoon';
import { LanguageSwitcher } from './LanguageSwitcher';
import { ProductSearchBox } from './ProductSearchBox';
import wordmark from '../../assets/ukun-wordmark-light.png';

/**
 * The entries of the navigation bar, in order. A `to` that is missing means the screen does not
 * exist yet: the entry is still shown, but as a disabled button instead of a link (see
 * `ComingSoon`). Keeping it as data is what stops the bar from becoming six near-identical blocks of
 * JSX, and puts "this one is not built yet" in a single place.
 */
const NAV_ITEMS = [
  { key: 'catalog', to: '/products' },
  { key: 'stores', to: '/stores' },
  { key: 'news' },
  { key: 'about' },
] as const satisfies readonly { key: string; to?: string }[];

/** Same idea for the account actions, which are the whole of v0.4 and have no route either. */
const ACCOUNT_ACTIONS = [
  { key: 'signIn', variant: 'outline-light' },
  { key: 'signUp', variant: 'light' },
] as const satisfies readonly { key: string; variant: 'light' | 'outline-light' }[];

/**
 * The navigation bar of every route, mounted once from `AppLayout`. It lives in `shared/ui/` next to
 * `SiteFooter` and for the same reason: it is part of the shell, not of any one feature.
 */
export function SiteHeader() {
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
          {/* La silueta del logotipo en blanco, no el original bicolor: los dos verdes de la marca
              sobre el verde de la barra no se distinguirían. El `alt` sí nombra la marca — es el
              enlace a inicio, y sin texto el enlace se queda sin nombre accesible. Las dimensiones
              intrínsecas van en los atributos para que la barra no salte al cargar la imagen. */}
          <img
            src={wordmark}
            alt={t('title')}
            className="navbar-brand__logo"
            width={882}
            height={254}
          />
        </Navbar.Brand>
        <Navbar.Toggle aria-label={t('nav.toggle')} />
        <Navbar.Collapse>
          <Nav>
            {NAV_ITEMS.map((item) =>
              'to' in item ? (
                <Nav.Link key={item.key} as={NavLink} to={item.to}>
                  {t(`nav.${item.key}`)}
                </Nav.Link>
              ) : (
                <ComingSoonNavItem key={item.key} label={t(`nav.${item.key}`)} />
              ),
            )}
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
            {ACCOUNT_ACTIONS.map((action) => (
              <ComingSoonButton
                key={action.key}
                label={t(`account.${action.key}`)}
                variant={action.variant}
              />
            ))}
          </div>
        </Navbar.Collapse>
      </Container>
    </Navbar>
  );
}
