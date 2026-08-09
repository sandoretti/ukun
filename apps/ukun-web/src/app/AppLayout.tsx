import { Suspense } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, NavLink, Outlet } from 'react-router-dom';
import { Container, Form, Nav, Navbar } from 'react-bootstrap';

import { useLocaleSync } from '../shared/hooks/useLocaleSync';
import { LoadingState } from '../shared/ui/LoadingState';
import { ProductSearchBox } from '../shared/ui/ProductSearchBox';
import { SiteFooter } from '../shared/ui/SiteFooter';

const LANGUAGES = ['es', 'en'] as const;

export function AppLayout() {
  const { t, i18n } = useTranslation('common');
  useLocaleSync();

  return (
    // Columna a toda la altura para que el pie quede abajo también en las pantallas cortas, y no
    // flotando a media página.
    <div className="d-flex flex-column min-vh-100">
      {/* Primer tabulador de la página: con una barra que lleva navegación, buscador e idioma, saltar
          al contenido ahorra media docena de paradas en cada ruta. Solo se ve al enfocarlo. */}
      <a href="#main-content" className="skip-link">
        {t('nav.skipToContent')}
      </a>

      {/* La barra lleva el verde de marca con texto blanco (6.02:1, AA — docs/07-frontend-ux-ui.md).
          `data-bs-theme="dark"` es lo que hace que Bootstrap 5.3 aclare marca, enlaces y toggler:
          sin él la barra queda casi blanca sobre blanco y no se distingue del contenido. */}
      <Navbar bg="primary" data-bs-theme="dark" expand="lg" sticky="top">
        <Container>
          <Navbar.Brand as={Link} to="/">
            {t('title')}
          </Navbar.Brand>
          <Navbar.Toggle aria-label={t('nav.toggle')} />
          <Navbar.Collapse>
            <Nav>
              <Nav.Link as={NavLink} to="/products">
                {t('nav.catalog')}
              </Nav.Link>
            </Nav>
            {/* El buscador es la acción principal de una tienda, así que vive en la barra y no
                dentro del formulario de filtros. Los controles vuelven al tema claro: un campo
                oscuro sobre el verde de marca es ilegible, y el tema de la barra se hereda si no se
                corta aquí. */}
            <div data-bs-theme="light" className="flex-grow-1 my-2 my-lg-0 mx-lg-4">
              <ProductSearchBox />
            </div>
            <Form.Group controlId="language-selector" className="d-flex align-items-center gap-2">
              <Form.Label className="mb-0 small">{t('language.label')}</Form.Label>
              <Form.Select
                data-bs-theme="light"
                size="sm"
                value={i18n.resolvedLanguage ?? 'es'}
                onChange={(event) => void i18n.changeLanguage(event.target.value)}
              >
                {LANGUAGES.map((language) => (
                  <option key={language} value={language}>
                    {t(`language.${language}`)}
                  </option>
                ))}
              </Form.Select>
            </Form.Group>
          </Navbar.Collapse>
        </Container>
      </Navbar>

      {/* Sin `Container` aquí: el ancho lo decide cada pantalla, porque la de inicio va a sangre. */}
      <main id="main-content" className="flex-grow-1">
        <Suspense fallback={<LoadingState />}>
          <Outlet />
        </Suspense>
      </main>

      <SiteFooter />
    </div>
  );
}
