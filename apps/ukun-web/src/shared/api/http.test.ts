import { http as mswHttp, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { server } from '../../test/msw/server';
import { startSignIn } from '../auth/signIn';
import { ProblemDetailError, http } from './http';

vi.mock('../auth/signIn', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../auth/signIn')>()),
  startSignIn: vi.fn(),
}));

/** A 401 with the body the gateway's entry point answers with. */
function unauthenticated() {
  return HttpResponse.json(
    { type: 'https://ukun.dev/errors/unauthenticated', title: 'UNAUTHENTICATED', status: 401, code: 'UNAUTHENTICATED' },
    { status: 401 },
  );
}

describe('http', () => {
  beforeEach(() => {
    vi.mocked(startSignIn).mockClear();
  });

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

  it('starts the sign-in flow when the session is gone', async () => {
    server.use(mswHttp.get('/api/products', () => unauthenticated()));

    await http.get('/api/products').catch(() => undefined);

    expect(vi.mocked(startSignIn)).toHaveBeenCalled();
  });

  it('leaves a request that allows an anonymous answer alone', async () => {
    server.use(mswHttp.get('/api/me', () => unauthenticated()));

    // The session probe: every visitor of a public screen gets this 401, and redirecting on it would
    // mean nobody can read the catalogue without signing in first.
    await http.get('/api/me', { allowAnonymous: true }).catch(() => undefined);

    expect(vi.mocked(startSignIn)).not.toHaveBeenCalled();
  });
});
