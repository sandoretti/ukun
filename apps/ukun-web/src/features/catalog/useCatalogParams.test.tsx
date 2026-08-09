import type { ReactNode } from 'react';
import { act, renderHook } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { useCatalogParams } from './useCatalogParams';

function wrapperAt(route: string) {
  return ({ children }: { children: ReactNode }) => <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter>;
}

/** The hook and the address bar it writes to, which is the other half of what this hook does. */
const renderAt = (route: string) =>
  renderHook(() => ({ ...useCatalogParams(), search: useLocation().search }), { wrapper: wrapperAt(route) });

describe('useCatalogParams', () => {
  it('reads the listing state from the URL', () => {
    const { result } = renderAt(
      '/products?name=sofa&typeId=1&priceMin=100&priceMax=800&sort=PRICE_DESC&page=2',
    );

    expect(result.current.criteria).toEqual({
      name: 'sofa',
      typeId: 1,
      priceMin: '100',
      priceMax: '800',
      sort: 'PRICE_DESC',
    });
    // The URL counts from one, like the pagination: `page=2` is the second page, not the third.
    expect(result.current.page).toBe(2);
  });

  it('ignores values that do not match the contract instead of forwarding them', () => {
    const { result } = renderAt('/products?typeId=abc&priceMin=-3&sort=BY_NAME&page=-1');

    expect(result.current.criteria).toMatchObject({
      typeId: null,
      priceMin: '',
      sort: 'PRICE_ASC',
    });
    expect(result.current.page).toBe(1);
  });

  it('treats page zero as the first page, which is the one the URL never writes', () => {
    const { result } = renderAt('/products?page=0');

    expect(result.current.page).toBe(1);

    act(() => result.current.goToPage(1));

    expect(result.current.search).toBe('');
  });

  it('goes back to the first page when a filter changes, keeping the rest', () => {
    const { result } = renderAt('/products?name=litera&page=3');

    act(() => result.current.updateFilters({ typeId: 2 }));

    expect(result.current.criteria).toMatchObject({ name: 'litera', typeId: 2 });
    expect(result.current.page).toBe(1);
    expect(result.current.search).toBe('?name=litera&typeId=2');
  });

  it('removes one filter without touching the others', () => {
    const { result } = renderAt('/products?name=sofa&typeId=1&priceMin=100');

    act(() => result.current.removeFilter('typeId'));

    expect(result.current.criteria).toMatchObject({ name: 'sofa', typeId: null, priceMin: '100' });
  });

  it('keeps the filters when paginating and drops everything when clearing', () => {
    const { result } = renderAt('/products?name=sofa');

    act(() => result.current.goToPage(2));
    expect(result.current.criteria).toMatchObject({ name: 'sofa' });
    expect(result.current.page).toBe(2);
    expect(result.current.search).toBe('?name=sofa&page=2');

    act(() => result.current.clearFilters());
    expect(result.current.criteria).toEqual({
      name: '',
      typeId: null,
      priceMin: '',
      priceMax: '',
      sort: 'PRICE_ASC',
    });
    expect(result.current.page).toBe(1);
  });
});
