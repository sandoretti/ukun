import type { ReactNode } from 'react';
import { act, renderHook } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { useAdminProductParams } from './useAdminProductParams';

function wrapperAt(route: string) {
  return ({ children }: { children: ReactNode }) => <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter>;
}

/** The hook and the address bar it writes to, which is the other half of what this hook does. */
const renderAt = (route: string) =>
  renderHook(() => ({ ...useAdminProductParams(), search: useLocation().search }), {
    wrapper: wrapperAt(route),
  });

describe('useAdminProductParams', () => {
  it('reads what is filtered, how it is sorted and which page it is on', () => {
    const { result } = renderAt(
      '/admin/products?q=sofá&sku=SOFA-CUERO-01&typeId=3&status=ALL&sort=PRICE_DESC&page=2',
    );

    expect(result.current.filters).toEqual({
      q: 'sofá',
      sku: 'SOFA-CUERO-01',
      typeId: 3,
      status: 'ALL',
      sort: 'PRICE_DESC',
    });
    // The URL counts from one, like the pagination: `page=2` is the second page, not the third.
    expect(result.current.page).toBe(2);
  });

  it('opens on the active products: the same default the contract has', () => {
    const { result } = renderAt('/admin/products');

    expect(result.current.filters).toEqual({
      q: '',
      sku: '',
      typeId: null,
      status: 'ACTIVE',
      sort: 'NAME_ASC',
    });
  });

  it('drops what the contract would reject instead of forwarding it', () => {
    const { result } = renderAt('/admin/products?status=WHATEVER&sort=RANDOM&sku=no%20es%20un%20sku&typeId=x&page=-1');

    expect(result.current.filters).toEqual({
      q: '',
      sku: '',
      typeId: null,
      status: 'ACTIVE',
      sort: 'NAME_ASC',
    });
    expect(result.current.page).toBe(1);
  });

  it('does not write the defaults: the plain path and the full query string are the same screen', () => {
    const { result } = renderAt('/admin/products?status=ALL&page=3');

    act(() => result.current.updateFilters({ status: 'ACTIVE' }));

    expect(result.current.search).toBe('');
  });

  it('goes back to the first page when a filter changes, keeping the rest', () => {
    const { result } = renderAt('/admin/products?status=ALL&q=sofá&page=3');

    act(() => result.current.updateFilters({ typeId: 1 }));

    expect(result.current.filters).toMatchObject({ status: 'ALL', q: 'sofá', typeId: 1 });
    expect(result.current.page).toBe(1);
  });

  it('goes back to the first page when the order changes: page 3 of one order is not page 3 of another', () => {
    const { result } = renderAt('/admin/products?page=3');

    act(() => result.current.updateFilters({ sort: 'UPDATED_DESC' }));

    expect(result.current.search).toBe('?sort=UPDATED_DESC');
  });

  it('clears the filters and keeps the order, which is not one of them', () => {
    const { result } = renderAt('/admin/products?q=sofá&typeId=3&status=DISCONTINUED&sort=PRICE_ASC&page=2');

    act(() => result.current.clearFilters());

    expect(result.current.filters).toEqual({
      q: '',
      sku: '',
      typeId: null,
      status: 'ACTIVE',
      sort: 'PRICE_ASC',
    });
    expect(result.current.page).toBe(1);
  });

  it('keeps the filters when the page changes: a page is not a different result', () => {
    const { result } = renderAt('/admin/products?sku=SOFA-CUERO-01');

    act(() => result.current.goToPage(2));

    expect(result.current.search).toBe('?sku=SOFA-CUERO-01&page=2');
  });
});
