import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { ProblemDetailError } from '../api/http';
import { renderWithProviders } from '../../test/renderWithProviders';
import { QueryBoundary } from './QueryBoundary';

/** The three flags of a query result this component reads, with the settled case as the default. */
function query(overrides: Partial<{ isPending: boolean; isError: boolean; error: unknown }> = {}) {
  return { isPending: false, isError: false, error: null, ...overrides };
}

/** The success branch, the same in every case: what changes is whether it is reached at all. */
const list = (items: readonly string[]) => (
  <ul>
    {items.map((item) => (
      <li key={item}>{item}</li>
    ))}
  </ul>
);

function renderBoundary(props: {
  query: ReturnType<typeof query>;
  data: readonly string[] | undefined;
  empty?: string;
}) {
  return renderWithProviders(<QueryBoundary {...props}>{list}</QueryBoundary>);
}

describe('QueryBoundary', () => {
  it('announces the wait while the read is in flight', () => {
    renderBoundary({ query: query({ isPending: true }), data: undefined });

    expect(screen.getByRole('status', { name: 'Cargando...' })).toBeDefined();
  });

  it('translates the code of the problem and never shows its raw detail', () => {
    const error = new ProblemDetailError(
      { code: 'RESOURCE_NOT_FOUND', detail: 'No product type with id 42' },
      404,
    );

    renderBoundary({ query: query({ isError: true, error }), data: undefined });

    expect(screen.getByText('No encontramos lo que buscas')).toBeDefined();
    expect(screen.queryByText('No product type with id 42')).toBeNull();
  });

  it('falls back to the generic code when the failure is not a ProblemDetail', () => {
    renderBoundary({
      query: query({ isError: true, error: new Error('socket hang up') }),
      data: undefined,
    });

    expect(screen.getByText('Algo ha fallado por nuestra parte. Inténtalo de nuevo')).toBeDefined();
  });

  it('keeps what already arrived when a later read fails', () => {
    // The catalog's next chunk failing must not wipe the products already on screen: dropping 48
    // rows to report the 49th is worse than the failure itself.
    renderBoundary({
      query: query({ isError: true, error: new Error('socket hang up') }),
      data: ['Sofás', 'Mesas'],
    });

    expect(screen.getByText('Sofás')).toBeDefined();
    expect(screen.queryByText('Algo ha fallado por nuestra parte. Inténtalo de nuevo')).toBeNull();
  });

  it('keeps the table on screen when the page that failed took the data away with it', async () => {
    // El caso real de los cinco listados paginados, y el que se escapaba: `keepPreviousData` solo
    // sostiene los datos mientras el estado es `pending`, así que al fallar la página siguiente
    // `data` vuelve a `undefined` y el boundary sustituía la tabla que se estaba leyendo por un
    // panel de error. Lo que se perdía no era la página que falló, era la que ya estaba delante.
    const { rerender } = renderBoundary({ query: query(), data: ['Sofás', 'Mesas'] });
    expect(screen.getByText('Sofás')).toBeDefined();

    // El efecto que recuerda la última lista corre tras el commit; `findBy*` de abajo espera a que
    // el rerender haya pintado, que es donde se lee.
    rerender(
      <QueryBoundary query={query({ isError: true, error: new Error('socket hang up') })} data={undefined}>
        {list}
      </QueryBoundary>,
    );

    expect(screen.getByText('Sofás')).toBeDefined();
    expect(screen.getByText('Mesas')).toBeDefined();
    // El fallo se cuenta al lado, no en lugar de la lista, y se anuncia: quien no ve la tabla
    // necesita que le digan que lo que está leyendo ya no es lo último.
    expect(screen.getByRole('status').textContent).toMatch(
      /No se han podido actualizar los resultados/u,
    );
    expect(screen.queryByText('Algo ha fallado por nuestra parte. Inténtalo de nuevo')).toBeNull();
  });

  it('says why there is nothing when the read succeeds and comes back empty', () => {
    renderBoundary({ query: query(), data: [], empty: 'Todavía no hay categorías' });

    expect(screen.getByText('Todavía no hay categorías')).toBeDefined();
  });

  it('falls back to the generic empty message when the screen gives none', () => {
    renderBoundary({ query: query(), data: [] });

    expect(screen.getByText('No hay resultados')).toBeDefined();
  });

  it('hands the data over once there is something to show', () => {
    renderBoundary({ query: query(), data: ['Sofás', 'Mesas'] });

    expect(screen.getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      'Sofás',
      'Mesas',
    ]);
  });
});
