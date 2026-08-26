import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { renderWithProviders } from '../../test/renderWithProviders';
import AdminHomePage from './AdminHomePage';

describe('AdminHomePage', () => {
  it('is the way in to the catalog, which is what the admin branch has today', () => {
    renderWithProviders(<AdminHomePage />, { route: '/admin' });

    expect(screen.getByRole('link', { name: /Productos/ }).getAttribute('href')).toBe('/admin/products');
  });

  it('announces nothing it cannot open yet', () => {
    renderWithProviders(<AdminHomePage />, { route: '/admin' });

    // Product types (RF-13) and employees (RF-11) are being built in parallel: a menu that lists
    // screens which do not exist is a menu that lies about the back-office.
    expect(screen.getAllByRole('link')).toHaveLength(1);
  });
});
