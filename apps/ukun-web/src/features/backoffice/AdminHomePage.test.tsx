import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { renderWithProviders } from '../../test/renderWithProviders';
import AdminHomePage from './AdminHomePage';

describe('AdminHomePage', () => {
  it('is the way in to the catalog and to the staff, which is what the admin branch has today', () => {
    renderWithProviders(<AdminHomePage />, { route: '/admin' });

    expect(screen.getByRole('link', { name: /Productos/ }).getAttribute('href')).toBe('/admin/products');
    expect(screen.getByRole('link', { name: /Empleados/ }).getAttribute('href')).toBe('/admin/employees');
  });

  it('announces nothing it cannot open yet', () => {
    renderWithProviders(<AdminHomePage />, { route: '/admin' });

    // Product types (RF-13) are still being built: a menu that lists screens which do not exist is a
    // menu that lies about the back-office. Employees (RF-11) landed with their two screens, so they
    // are the second entry rather than a promise.
    expect(screen.getAllByRole('link')).toHaveLength(2);
  });
});
