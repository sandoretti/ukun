import { http as mswHttp, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';

import { server } from '../../mocks/server';
import { ProblemDetailError, http } from './http';

describe('http', () => {
  it('normalises a ProblemDetail into ProblemDetailError, extensions included', async () => {
    server.use(
      mswHttp.get('/api/products', () =>
        HttpResponse.json(
          {
            type: 'https://ukun.dev/errors/duplicate-value',
            title: 'DUPLICATE_VALUE',
            status: 409,
            detail: 'sku already exists',
            code: 'DUPLICATE_VALUE',
            traceId: 'abc',
            field: 'sku',
          },
          { status: 409 },
        ),
      ),
    );

    const error = await http.get('/api/products').catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(ProblemDetailError);
    const problem = error as ProblemDetailError;
    expect(problem.status).toBe(409);
    expect(problem.code).toBe('DUPLICATE_VALUE');
    // The extensions are what feeds `t('errors:CODE', extensions)`; the fixed fields are not part
    // of them.
    expect(problem.extensions).toEqual({ field: 'sku' });
  });

  it('falls back to the generic code when the body is not a ProblemDetail', async () => {
    server.use(mswHttp.get('/api/products', () => new HttpResponse('boom', { status: 500 })));

    const error = (await http.get('/api/products').catch((caught: unknown) => caught)) as ProblemDetailError;

    expect(error.code).toBe('INTERNAL_ERROR');
    expect(error.status).toBe(500);
  });
});
