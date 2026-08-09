import type { ReactNode } from 'react';
import { act, renderHook } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { useCatalogParams } from './useCatalogParams';

function wrapperAt(route: string) {
  return ({ children }: { children: ReactNode }) => <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter>;
}

describe('useCatalogParams', () => {
  it('reads the listing state from the URL', () => {
    const { result } = renderHook(() => useCatalogParams(), {
      wrapper: wrapperAt('/products?name=sofa&typeId=1&priceMin=100&priceMax=800&sort=PRICE_DESC&page=2'),
    });

    expect(result.current.filters).toMatchObject({
      name: 'sofa',
      typeId: 1,
      priceMin: '100',
      priceMax: '800',
      sort: 'PRICE_DESC',
      page: 2,
    });
  });

  it('ignores values that do not match the contract instead of forwarding them', () => {
    const { result } = renderHook(() => useCatalogParams(), {
      wrapper: wrapperAt('/products?typeId=abc&priceMin=-3&sort=BY_NAME&page=-1'),
    });

    expect(result.current.filters).toMatchObject({
      typeId: null,
      priceMin: '',
      sort: 'PRICE_ASC',
      page: 0,
    });
  });

  it('goes back to the first page when a filter changes', () => {
    const { result } = renderHook(() => useCatalogParams(), { wrapper: wrapperAt('/products?page=3') });

    act(() => {
      result.current.applyFilters({ name: 'litera', typeId: null, priceMin: '', priceMax: '', sort: 'PRICE_ASC' });
    });

    expect(result.current.filters.page).toBe(0);
    expect(result.current.filters.name).toBe('litera');
  });

  it('keeps the filters when paginating and drops everything when clearing', () => {
    const { result } = renderHook(() => useCatalogParams(), { wrapper: wrapperAt('/products?name=sofa') });

    act(() => result.current.goToPage(2));
    expect(result.current.filters).toMatchObject({ name: 'sofa', page: 2 });

    act(() => result.current.clearFilters());
    expect(result.current.filters).toMatchObject({ name: '', typeId: null, sort: 'PRICE_ASC', page: 0 });
  });
});
