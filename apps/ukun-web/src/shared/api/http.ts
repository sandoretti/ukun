import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';

import { startSignIn } from '../auth/signIn';
import type { components } from '../../types/api';

declare module 'axios' {
  interface AxiosRequestConfig {
    /**
     * Whether a `401` on this request is an expected answer rather than a dead session. Only the
     * session probe sets it: `GET /api/me` answers 401 to every anonymous visitor, and the public
     * screens would otherwise send anyone who opens the home page to authenticate.
     *
     * It is declared intent and not a check on the URL: `http.ts` is not the place that knows which
     * route the probe lives at.
     */
    allowAnonymous?: boolean;
  }
}

type ProblemDetail = components['schemas']['ProblemDetail'];
export type ErrorCode = components['schemas']['ErrorCode'];
export type ValidationError = components['schemas']['ValidationError'];

/** Extensions of the ProblemDetail beyond its fixed fields, for `t('errors:CODE', extensions)`. */
const FIXED_PROBLEM_FIELDS = ['type', 'title', 'status', 'detail', 'instance', 'code', 'traceId', 'errors'];

/**
 * Any 4xx/5xx from the API, normalised to the closed registry of `docs/12-error-contract.md`.
 * Screens translate `code` (ADR-0019); `detail` is fixed English for the operator and is never
 * rendered.
 */
export class ProblemDetailError extends Error {
  readonly status: number;
  readonly code: ErrorCode;
  readonly detail?: string;
  readonly traceId?: string;
  readonly errors: ValidationError[];
  readonly extensions: Record<string, unknown>;

  constructor(problem: Partial<ProblemDetail>, status: number) {
    super(problem.detail ?? problem.title ?? problem.code ?? 'INTERNAL_ERROR');
    this.name = 'ProblemDetailError';
    this.status = problem.status ?? status;
    this.code = problem.code ?? 'INTERNAL_ERROR';
    this.detail = problem.detail;
    this.traceId = problem.traceId;
    this.errors = problem.errors ?? [];
    this.extensions = Object.fromEntries(
      Object.entries(problem).filter(([key]) => !FIXED_PROBLEM_FIELDS.includes(key)),
    );
  }
}

/**
 * Narrows whatever TanStack Query hands back in `error` to the shape screens paint. Everything this
 * instance rejects with is already a `ProblemDetailError`; the fallback covers a throw from
 * somewhere else in the query function.
 */
export function toProblem(error: unknown): ProblemDetailError {
  return error instanceof ProblemDetailError ? error : new ProblemDetailError({ code: 'INTERNAL_ERROR' }, 0);
}

const MUTATING_METHODS = ['post', 'put', 'patch', 'delete'];

const CSRF_COOKIE = 'XSRF-TOKEN';

function readCookie(name: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((entry) => entry.startsWith(`${name}=`))
    ?.slice(name.length + 1);
}

/**
 * The CSRF token the gateway last wrote, as the literal string it expects back. The gateway uses
 * `ServerCsrfTokenRequestAttributeHandler` instead of the default XOR one precisely so that cookie
 * and header are the same value, and it leaves the cookie readable (`HttpOnly=false`) on purpose:
 * this one is meant to be echoed, unlike the session cookie.
 *
 * Exported because the sign-out form needs the same string in a hidden field — one cookie reader in
 * the whole app, not two that can drift apart.
 */
export function csrfToken(): string | undefined {
  const raw = readCookie(CSRF_COOKIE);

  return raw === undefined ? undefined : decodeURIComponent(raw);
}

/**
 * The single axios instance of the app (ADR-0010). Every feature goes through it: no bare `fetch`,
 * no second instance. `baseURL` empty means same origin — the gateway in production, the Vite dev
 * proxy in development; either way a real `ukun-api` answers.
 */
export const http = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '',
  withCredentials: true,
  headers: { Accept: 'application/json' },
});

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const method = (config.method ?? 'get').toLowerCase();
  if (MUTATING_METHODS.includes(method)) {
    const token = csrfToken();
    if (token) {
      config.headers.set('X-XSRF-TOKEN', token);
    }
  }
  return config;
});

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ProblemDetail>) => {
    const status = error.response?.status;

    // A dead session is not a screen-level error: it is handled once, here, by starting the flow
    // again from wherever the reader was. The destination is the gateway's own `oauth2Login`
    // endpoint — `/login` is a reserved prefix that answers 404 to the SPA, never a screen.
    //
    // A request that says it can be answered anonymously is exempt: its 401 means "no session",
    // which is a perfectly good state for a public screen to be in.
    if (status === 401 && error.config?.allowAnonymous !== true) {
      startSignIn();
    }

    if (status !== undefined) {
      const body = error.response?.data;
      const problem = typeof body === 'object' && body !== null ? body : {};
      return Promise.reject(new ProblemDetailError(problem, status));
    }

    // No response at all (network down, request cancelled): there is no `code` to translate, so it
    // takes the generic one rather than inventing a value outside the closed registry.
    return Promise.reject(new ProblemDetailError({ code: 'INTERNAL_ERROR' }, 0));
  },
);
