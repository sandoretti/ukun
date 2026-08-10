import { Suspense } from 'react';
import { useTranslation } from 'react-i18next';
import { Outlet } from 'react-router-dom';

import { useLocaleSync } from '../shared/hooks/useLocaleSync';
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

  return (
    // Columna a toda la altura para que el pie quede abajo también en las pantallas cortas, y no
    // flotando a media página.
    <div className="d-flex flex-column min-vh-100">
      {/* Primer tabulador de la página: con una barra que lleva navegación, buscador e idioma, saltar
          al contenido ahorra media docena de paradas en cada ruta. Solo se ve al enfocarlo. */}
      <a href="#main-content" className="skip-link">
        {t('nav.skipToContent')}
      </a>

      <SiteHeader />

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
