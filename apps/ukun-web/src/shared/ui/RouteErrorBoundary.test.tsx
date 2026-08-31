import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { renderWithProviders } from '../../test/renderWithProviders';
import { RouteErrorBoundary } from './RouteErrorBoundary';

/** En constantes y no en el JSX: la regla de i18n vigila `src/` entera, tests incluidos. */
const SHELL = 'La barra de siempre';
const HEALTHY = 'La otra pantalla';
const GO_ON = 'Seguir';

function Explodes(): never {
  throw new Error('el módulo de esta ruta no se pudo descargar');
}

/** El cascarón: algo fuera del borde —que tiene que sobrevivir— y la ruta dentro. */
function Shell() {
  return (
    <>
      <p>{SHELL}</p>
      <RouteErrorBoundary>
        <Routes>
          <Route path="/roto" element={<Explodes />} />
          <Route path="/sano" element={<h1>{HEALTHY}</h1>} />
        </Routes>
      </RouteErrorBoundary>
      <Link to="/sano">{GO_ON}</Link>
    </>
  );
}

describe('RouteErrorBoundary', () => {
  beforeEach(() => {
    // React escribe el error por consola aunque el borde lo recoja, y `componentDidCatch` añade el
    // suyo a propósito. Silenciarlo aquí evita ensuciar la salida sin dejar de ejercitarlo.
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('sustituye la pantalla rota y deja en pie el resto del cascarón', () => {
    renderWithProviders(<Shell />, { route: '/roto' });

    // Lo que antes pasaba era esto pero entero: React desmontaba el árbol y no quedaba nada, ni
    // siquiera una forma de irse a otro sitio.
    expect(screen.getByText(SHELL)).toBeDefined();
    expect(screen.getByRole('link', { name: GO_ON })).toBeDefined();

    // El mensaje sale del registro cerrado de errores, no de una cadena inventada aquí.
    expect(screen.getByText('Algo ha fallado por nuestra parte. Inténtalo de nuevo')).toBeDefined();
    expect(screen.getByRole('button', { name: 'Volver a cargar la página' })).toBeDefined();
  });

  it('no se queda enganchado: irse a otra ruta lo rearma', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Shell />, { route: '/roto' });

    await user.click(screen.getByRole('link', { name: GO_ON }));

    expect(screen.getByRole('heading', { level: 1, name: HEALTHY })).toBeDefined();
    expect(screen.queryByRole('button', { name: 'Volver a cargar la página' })).toBeNull();
  });

  it('deja pasar la pantalla cuando no hay nada que recoger', () => {
    renderWithProviders(<Shell />, { route: '/sano' });

    expect(screen.getByRole('heading', { level: 1, name: HEALTHY })).toBeDefined();
  });
});
