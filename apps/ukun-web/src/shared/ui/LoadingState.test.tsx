import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { renderWithProviders } from '../../test/renderWithProviders';
import { LoadingState } from './LoadingState';

describe('LoadingState', () => {
  it('announces the wait as a named status, not as a silent spinner', () => {
    // With the real i18n bundles and not a mocked `t`: what is being checked is that the wait has a
    // name a screen reader can read, and a mock that echoes the key would pass without it.
    renderWithProviders(<LoadingState />);

    expect(screen.getByRole('status', { name: 'Cargando...' })).toBeDefined();
  });
});
