import { HttpResponse } from 'msw';

import type { components } from '../../types/api';

type ProblemDetail = components['schemas']['ProblemDetail'];
type ErrorCode = components['schemas']['ErrorCode'];

/**
 * The `detail` every double answers with. It is fixed English written for the operator and for the
 * logs, and **no screen ever renders it** (`docs/12-error-contract.md`): the frontend translates the
 * `code`. It is exported so a test can assert its absence without copying the sentence — a literal
 * repeated in the assertion and in the stub drifts apart the day one of the two is reworded, and the
 * test keeps passing while checking nothing.
 */
export const OPERATOR_DETAIL = 'Fixed English detail nobody should ever read on screen';

/**
 * An error of this contract, shaped like the ones `ukun-api` really sends: `type` derived from the
 * `code` exactly as `docs/12-error-contract.md` §2 derives it, the media type of RFC 9457, and the
 * extensions of the registry passed through as they come.
 *
 * One factory for every group of handlers — catalogue, profile, cart — because there is one error
 * contract, not one per feature. Three copies of it with three different argument orders is what
 * this replaces.
 */
export function problem(code: ErrorCode, status: number, extensions: Record<string, unknown> = {}) {
  return HttpResponse.json<ProblemDetail>(
    {
      type: `https://ukun.dev/errors/${code.toLowerCase().replaceAll('_', '-')}`,
      title: code,
      status,
      detail: OPERATOR_DETAIL,
      code,
      traceId: '00000000000000000000000000000000',
      ...extensions,
    },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  );
}
