import { http as mswHttp, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { server } from '../../test/msw/server';
import { unauthenticated } from '../../test/msw/sessionHandlers';
import { sendToSignIn } from '../auth/signIn';
import { ProblemDetailError, http } from './http';

vi.mock('../auth/signIn', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../auth/signIn')>()),
  sendToSignIn: vi.fn(),
}));

describe('http', () => {
  beforeEach(() => {
    vi.mocked(sendToSignIn).mockClear();
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

  it('sends the reader to the sign-in screen, told why, when the session is gone', async () => {
    server.use(mswHttp.get('/api/products', () => unauthenticated()));

    await http.get('/api/products').catch(() => undefined);

    // The reason travels with the redirect: a session that dies mid-visit is a surprise, and the
    // screen is the only place that can say so.
    expect(vi.mocked(sendToSignIn)).toHaveBeenCalledWith('expired');
  });

  it('leaves a request that allows an anonymous answer alone', async () => {
    server.use(mswHttp.get('/api/me', () => unauthenticated()));

    // The session probe: every visitor of a public screen gets this 401, and redirecting on it would
    // mean nobody can read the catalogue without signing in first.
    await http.get('/api/me', { allowAnonymous: true }).catch(() => undefined);

    expect(vi.mocked(sendToSignIn)).not.toHaveBeenCalled();
  });
});
