import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';

import type { components } from '../../types/api';

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

function readCookie(name: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((entry) => entry.startsWith(`${name}=`))
    ?.slice(name.length + 1);
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
    const token = readCookie('XSRF-TOKEN');
    if (token) {
      config.headers.set('X-XSRF-TOKEN', decodeURIComponent(token));
    }
  }
  return config;
});

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ProblemDetail>) => {
    const status = error.response?.status;

    // A 401 is a dead session, not a screen-level error: it is handled once, here.
    if (status === 401 && window.location.pathname !== '/login') {
      window.location.assign('/login');
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
