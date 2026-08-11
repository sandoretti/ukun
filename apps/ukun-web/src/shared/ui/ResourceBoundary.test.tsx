import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { renderWithProviders } from '../../test/renderWithProviders';
import { ProblemDetailError } from '../api/http';
import { ResourceBoundary } from './ResourceBoundary';

type QueryStatus = { isPending: boolean; isError: boolean; error: unknown };

const pending: QueryStatus = { isPending: true, isError: false, error: null };
const failed: QueryStatus = {
  isPending: false,
  isError: true,
  error: new ProblemDetailError({ code: 'INTERNAL_ERROR', detail: 'Unexpected failure' }, 500),
};
const settled: QueryStatus = { isPending: false, isError: false, error: null };

type Profile = { name: string };

function renderBoundary(query: QueryStatus, data: Profile | null | undefined, empty?: string) {
  return renderWithProviders(
    <ResourceBoundary query={query} data={data} empty={empty}>
      {(profile) => <p>{profile.name}</p>}
    </ResourceBoundary>,
  );
}

describe('ResourceBoundary', () => {
  it('announces the wait while the resource is in flight', () => {
    renderBoundary(pending, undefined);

    expect(screen.getByRole('status', { name: 'Cargando...' })).toBeDefined();
  });

  it('paints the translated code of a failed read, never the raw detail', () => {
    renderBoundary(failed, undefined);

    expect(screen.getByText('Algo ha fallado por nuestra parte. Inténtalo de nuevo')).toBeDefined();
    expect(screen.queryByText('Unexpected failure')).toBeNull();
  });

  it('says why there is nothing rather than leaving a blank area', () => {
    renderBoundary(settled, null, 'Todavía no hay perfil');

    expect(screen.getByText('Todavía no hay perfil')).toBeDefined();
  });

  it('hands the resource over on success', () => {
    renderBoundary(settled, { name: 'Ana' });

    expect(screen.getByText('Ana')).toBeDefined();
  });

  it('keeps the resource on screen when a refetch fails over it', () => {
    // The rule the component exists to enforce: an error replaces the screen only when there is
    // nothing to show. Dropping a profile that is already painted to announce a failed refresh is
    // worse than the failure.
    renderBoundary(failed, { name: 'Ana' });

    expect(screen.getByText('Ana')).toBeDefined();
    expect(screen.queryByText('Algo ha fallado por nuestra parte. Inténtalo de nuevo')).toBeNull();
  });
});
