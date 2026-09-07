import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { renderWithProviders } from '../../test/renderWithProviders';
import AboutPage from './AboutPage';

function renderAbout() {
  renderWithProviders(<AboutPage />, { route: '/about' });
}

describe('AboutPage', () => {
  it('leads with a single h1 and lays the bands out under it, in order', () => {
    renderAbout();

    const headingOne = screen.getAllByRole('heading', { level: 1 });
    expect(headingOne).toHaveLength(1);
    expect(headingOne[0].textContent).toBe('Doce años haciendo muebles que aguanten');

    // The order is the content of `AboutPage`, so it is asserted whole and in sequence: the dense
    // bands are interleaved with the narrative ones on purpose, and four long sections in a row is
    // where a reader stops. Five of these six moved here from the landing page — `commitments` is
    // the one written for this screen.
    const headingTwo = screen.getAllByRole('heading', { level: 2 });
    expect(headingTwo.map((heading) => heading.textContent)).toEqual([
      'Cómo hemos llegado hasta aquí',
      'De qué está hecho y cómo llega',
      'Crea ambientes que reflejen tu estilo',
      'A qué nos comprometemos',
      'Preguntas frecuentes',
      'Ya sabes de dónde sale',
    ]);
  });

  it('reads the brand figures as pairs of a figure and its caption', () => {
    renderAbout();

    // A `<dl>`, not a row of boxes: figure and caption are a term and its definition, and the
    // pairing has to survive being read out loud. The digits come from the component and the
    // grouping from `Intl`, which is why the assertion is on the formatted value.
    const years = screen.getByText('años amueblando casas');
    expect(years.previousElementSibling?.textContent).toBe('12');
    expect(screen.getByText('pedidos entregados').previousElementSibling?.textContent).toBe(
      '45.000+',
    );
  });

  it('states the four commitments with what each one actually covers', () => {
    renderAbout();

    const commitments = screen.getByRole('heading', { name: 'A qué nos comprometemos' }).closest('section');
    expect(commitments).not.toBeNull();

    // Each term is also a step of the page's heading outline, so it is an `<h3>` inside the `<dt>`:
    // four titles outside the outline would be four bands a screen reader skips over.
    const terms = within(commitments!).getAllByRole('heading', { level: 3 });
    expect(terms.map((term) => term.textContent)).toEqual([
      'Cinco años en estructuras, dos en tapicería',
      'Repuestos sueltos, también con la garantía vencida',
      'La disponibilidad real, tienda por tienda',
      'De tu tarjeta guardamos cuatro cifras',
    ]);

    // The card one is the claim worth pinning down: `docs/08` §5 is why this system keeps neither
    // the full number nor the security code, and copy that promised otherwise would be disproved by
    // the checkout screen next door.
    expect(
      within(commitments!).getByText(/nunca el número completo ni el código de seguridad/),
    ).toBeDefined();
  });

  it('opens one answer of the FAQ at a time', async () => {
    const user = userEvent.setup();
    renderAbout();

    const question = screen.getByRole('button', { name: /¿Cuánto tarda el envío\?/ });
    expect(question.getAttribute('aria-expanded')).toBe('false');

    await user.click(question);

    expect(question.getAttribute('aria-expanded')).toBe('true');
    expect(screen.getByText(/Entre dos y cinco días laborables/)).toBeDefined();
  });

  it('closes with two ways out, and the second one is the stores', () => {
    renderAbout();

    // The difference from the landing page's closing, which sends everyone to the catalogue: whoever
    // reaches the bottom of this page has just read that samples can be taken home and that stock is
    // shown store by store, so the stores are a destination and not a footnote.
    expect(screen.getByRole('link', { name: 'Ver el catálogo' }).getAttribute('href')).toBe(
      '/products',
    );
    expect(screen.getByRole('link', { name: 'Ver las tiendas' }).getAttribute('href')).toBe(
      '/stores',
    );
  });

  it('tells the story with drawings that carry no meaning of their own', () => {
    renderAbout();

    // Same call the landing page makes: what a drawing suggests is written right next to it, so it
    // stays out of the accessibility tree and nothing is announced twice.
    expect(screen.queryAllByRole('img')).toHaveLength(0);
  });
});
