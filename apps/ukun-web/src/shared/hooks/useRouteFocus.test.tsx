import { StrictMode } from 'react';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { renderWithProviders } from '../../test/renderWithProviders';
import { useRouteFocus } from './useRouteFocus';

/** En constantes y no escritos en el JSX: la regla de i18n vigila `src/` entera, tests incluidos. */
const GO_ON = 'Seguir';
const OTHER_SCREEN = 'La otra pantalla';

/**
 * El cascarón, reducido a lo que este hook necesita: un `<main>` enfocable. El hook se llama aquí,
 * **dentro** del `StrictMode` que monta `Shell`, porque es la llamada la que tiene que doblarse.
 */
function Content() {
  useRouteFocus();

  return (
    <main id="main-content" tabIndex={-1}>
      <Routes>
        <Route path="/" element={<Link to="/other">{GO_ON}</Link>} />
        <Route path="/other" element={<h1>{OTHER_SCREEN}</h1>} />
      </Routes>
    </main>
  );
}

/**
 * Montado en `StrictMode`, que es como lo monta `main.tsx`, y no es un detalle del andamiaje.
 *
 * `StrictMode` invoca el efecto dos veces al montar. La primera versión de este hook guardaba el
 * primer render con una bandera: la primera pasada la apagaba y la segunda ya la encontraba apagada,
 * así que robaba el foco justo en la carga inicial que quería respetar. Este test pasaba porque no
 * montaba en `StrictMode`; lo cazó el CI, en el navegador, con el primer tabulador de una página
 * recién cargada que ya no llegaba al salto al contenido.
 */
function Shell() {
  return (
    <StrictMode>
      <Content />
    </StrictMode>
  );
}

describe('useRouteFocus', () => {
  it('no roba el foco al abrir la aplicación', () => {
    renderWithProviders(<Shell />);

    // Al entrar, el foco pertenece al documento: moverlo al contenido dejaría el salto al contenido
    // detrás del cursor, inútil antes de que a nadie le dé tiempo a usarlo.
    expect(document.activeElement).toBe(document.body);
  });

  it('lleva el foco al contenido cuando cambia la ruta', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Shell />);

    await user.click(screen.getByRole('link', { name: GO_ON }));

    // Sin esto el cambio de pantalla es mudo: en un SPA no se carga ningún documento, así que el
    // foco se queda en el enlace pulsado —o en `<body>`, si ese enlace ha dejado de existir— y un
    // lector de pantalla no anuncia nada. Es lo que hacía que confirmar un pedido o dar de alta una
    // línea de stock no se oyera.
    await waitFor(() => {
      expect(document.activeElement).toBe(document.getElementById('main-content'));
    });
    expect(screen.getByRole('heading', { level: 1, name: OTHER_SCREEN })).toBeDefined();
  });
});
