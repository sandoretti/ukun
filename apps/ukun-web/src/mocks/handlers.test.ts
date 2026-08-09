import { describe, expect, it } from 'vitest';

import { catalogHandlers } from './catalog';
import { handlers } from './handlers';

describe('mock handlers', () => {
  /**
   * A request that matches nothing forces MSW to evaluate every pattern — which is exactly what the
   * browser does with each Vite module request. A single unparseable path (a custom method such as
   * `/api/admin/products/{id}:discontinue` generated as two adjacent parameters) throws there and
   * takes the whole app down with a blank page, while a test that matches an earlier handler never
   * reaches it.
   */
  it('parses every path pattern, including the custom methods of the contract', async () => {
    const request = new Request('http://localhost/not-a-mocked-path');

    for (const handler of [...catalogHandlers, ...handlers]) {
      await expect(handler.parse({ request })).resolves.toBeDefined();
    }
  });
});
