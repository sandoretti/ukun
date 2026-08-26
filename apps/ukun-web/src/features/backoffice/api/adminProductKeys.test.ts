import { describe, expect, it } from 'vitest';

import {
  adminProductKeys,
  PAGE_SIZE,
  toApiFilters,
  type AdminProductFilterInput,
} from './adminProductKeys';

const filters: AdminProductFilterInput = {
  q: 'sofá',
  sku: '',
  typeId: 3,
  status: 'ALL',
  sort: 'PRICE_DESC',
};

const apiFilters = toApiFilters(filters, 1);

describe('adminProductKeys', () => {
  it('nests every key under the same root, so one prefix invalidates the whole catalog', () => {
    for (const key of [
      adminProductKeys.lists(),
      adminProductKeys.list(apiFilters),
      adminProductKeys.details(),
      adminProductKeys.detail(7),
    ]) {
      expect(key.slice(0, adminProductKeys.all.length)).toEqual([...adminProductKeys.all]);
    }
  });

  it('keeps listings and sheets apart: invalidating a listing does not reach a sheet', () => {
    expect(adminProductKeys.detail(7).slice(0, 2)).not.toEqual([...adminProductKeys.lists()]);
  });

  it('does not share a root with the public catalog, which answers something else entirely', () => {
    // A discontinued product exists here and not there, so the two reads are not the same cache entry
    // under two names.
    expect(adminProductKeys.all).not.toContain('products');
  });

  it('tells one set of filters from another', () => {
    expect(adminProductKeys.list(apiFilters)).not.toEqual(
      adminProductKeys.list(toApiFilters({ ...filters, status: 'ACTIVE' }, 1)),
    );
  });
});

describe('toApiFilters', () => {
  it('translates the page: the URL counts from one and the API from zero', () => {
    expect(toApiFilters(filters, 1).page).toBe(0);
    expect(toApiFilters(filters, 4).page).toBe(3);
  });

  it('carries the criteria through untouched, and asks for one page of the table', () => {
    expect(toApiFilters(filters, 2)).toEqual({ ...filters, page: 1, size: PAGE_SIZE });
  });
});
