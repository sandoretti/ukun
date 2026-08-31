import { Suspense } from 'react';
import { useTranslation } from 'react-i18next';
import { Outlet } from 'react-router-dom';

import { SessionMenu } from '../features/auth/components/SessionMenu';
import { useRestoreReturnTo } from '../features/auth/useRestoreReturnTo';
import { CartIndicator } from '../features/cart/components/CartIndicator';
import { useLocaleSync } from '../shared/hooks/useLocaleSync';
import { MAIN_CONTENT_ID, useRouteFocus } from '../shared/hooks/useRouteFocus';
import { LoadingState } from '../shared/ui/LoadingState';
import { SiteFooter } from '../shared/ui/SiteFooter';
import { SiteHeader } from '../shared/ui/SiteHeader';

/**
 * The shell every route is painted inside: skip link, bar, content and foot, in that order. What is
 * in the bar and what is in the foot is each component's business — this file only owns the frame
 * and where the route goes in it.
 */
export function AppLayout() {
  const { t } = useTranslation('common');
  useLocaleSync();
  useRouteFocus();
  // Volver de autenticarse aterriza en `/`, siempre: el destino lo guarda y lo restaura el cliente,
  // y este es el único sitio que lo hace.
  useRestoreReturnTo();

  return (
    // Columna a toda la altura para que el pie quede abajo también en las pantallas cortas, y no
    // flotando a media página.
    <div className="d-flex flex-column min-vh-100">
      {/* Primer tabulador de la página: con una barra que lleva navegación, buscador e idioma, saltar
          al contenido ahorra media docena de paradas en cada ruta. Solo se ve al enfocarlo. */}
      <a href={`#${MAIN_CONTENT_ID}`} className="skip-link">
        {t('nav.skipToContent')}
      </a>

      {/* `<header>` de verdad, y no solo la barra: `Navbar` de react-bootstrap renderiza un `<nav>`,
          así que la página tenía landmark de navegación pero ninguno de cabecera. Quien recorre la
          página por regiones —que es como se orienta quien no la ve— no encontraba el banner.

          El bloque de cuenta se compone aquí: la barra vive en `shared/ui/` y no puede importar de
          una feature, pero esta capa sí — es la que ya monta las pantallas. */}
      <header>
        <SiteHeader>
          <CartIndicator />
          <SessionMenu />
        </SiteHeader>
      </header>

      {/* Sin `Container` aquí: el ancho lo decide cada pantalla, porque la de inicio va a sangre. */}
      <main id={MAIN_CONTENT_ID} tabIndex={-1} className="flex-grow-1">
        <Suspense fallback={<LoadingState />}>
          <Outlet />
        </Suspense>
      </main>

      <SiteFooter />
    </div>
  );
}
