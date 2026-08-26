import type { ReactNode } from 'react';
import { act, renderHook } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { useStockParams } from './useStockParams';

function wrapperAt(route: string) {
  return ({ children }: { children: ReactNode }) => <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter>;
}

/** The hook and the address bar it writes to, which is the other half of what this hook does. */
const renderAt = (route: string) =>
  renderHook(() => ({ ...useStockParams(), search: useLocation().search }), { wrapper: wrapperAt(route) });

describe('useStockParams', () => {
  it('reads which half is listed, what is filtered and which page it is on', () => {
    const { result } = renderAt('/backoffice/stock?status=NOT_STOCKED&sku=SOFA-CUERO-01&q=sofá&page=2');

    expect(result.current.filters).toEqual({
      status: 'NOT_STOCKED',
      sku: 'SOFA-CUERO-01',
      q: 'sofá',
    });
    // The URL counts from one, like the pagination: `page=2` is the second page, not the third.
    expect(result.current.page).toBe(2);
  });

  it('upper-cases a SKU instead of treating it as a different search', () => {
    const { result } = renderAt('/backoffice/stock?sku=sofa-cuero-01');

    expect(result.current.filters.sku).toBe('SOFA-CUERO-01');
  });

  it('drops what the contract would reject instead of forwarding it', () => {
    const { result } = renderAt('/backoffice/stock?status=WHATEVER&sku=no%20es%20un%20sku&page=-1');

    expect(result.current.filters).toMatchObject({ status: 'STOCKED', sku: '' });
    expect(result.current.page).toBe(1);
  });

  it('does not write the defaults: the plain path and the full query string are the same screen', () => {
    const { result } = renderAt('/backoffice/stock?page=3');

    act(() => result.current.updateFilters({ status: 'STOCKED' }));

    expect(result.current.search).toBe('');
  });

  it('goes back to the first page when a filter changes, keeping the rest', () => {
    const { result } = renderAt('/backoffice/stock?status=NOT_STOCKED&q=mesa&page=3');

    act(() => result.current.updateFilters({ sku: 'MESA-CENTRO-01' }));

    expect(result.current.filters).toMatchObject({ status: 'NOT_STOCKED', q: 'mesa', sku: 'MESA-CENTRO-01' });
    expect(result.current.page).toBe(1);
  });

  it('keeps the tab when the filters are cleared: that is not changing screen', () => {
    const { result } = renderAt('/backoffice/stock?status=NOT_STOCKED&sku=SOFA-CUERO-01&q=sofá&page=2');

    act(() => result.current.clearFilters());

    expect(result.current.filters).toEqual({ status: 'NOT_STOCKED', sku: '', q: '' });
    expect(result.current.page).toBe(1);
  });

  it('keeps the filters when the view changes, and goes back to the first page', () => {
    const { result } = renderAt('/backoffice/stock?q=sof%C3%A1&page=3');

    act(() => result.current.updateFilters({ status: 'NOT_STOCKED' }));

    expect(result.current.filters).toMatchObject({ status: 'NOT_STOCKED', q: 'sofá' });
    // Page 3 of one view says nothing about another.
    expect(result.current.page).toBe(1);
  });

  it('keeps the filters when the page changes: a page is not a different result', () => {
    const { result } = renderAt('/backoffice/stock?sku=SOFA-CUERO-01');

    act(() => result.current.goToPage(2));

    expect(result.current.search).toBe('?sku=SOFA-CUERO-01&page=2');
  });
});
