import { toProblem } from './http';

/** A 5xx or a dropped connection is worth exactly one more attempt, not TanStack Query's default three. */
const MAX_RETRIES = 1;

/**
 * When a failed read is worth asking again.
 *
 * A 4xx is the server's final answer, not a hiccup: a discontinued product does not come back, and a
 * profile the account is not allowed to read does not become readable by asking three more times.
 * Retrying anyway would also hold a spinner on screen for seconds —the default backoff— before the
 * screen that explains the failure appears.
 */
export function retryUnlessClientError(failureCount: number, error: unknown): boolean {
  const { status } = toProblem(error);
  const isClientError = status >= 400 && status < 500;

  return !isClientError && failureCount < MAX_RETRIES;
}
