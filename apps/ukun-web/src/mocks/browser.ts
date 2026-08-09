import { setupWorker } from 'msw/browser';
import { catalogHandlers } from './catalog';
import { handlers } from './handlers';

// Hand-written handlers go first: MSW resolves with the first match, and `handlers.ts` is the file
// `npm run generate:mocks` regenerates from the OpenAPI with a stub per operation.
export const worker = setupWorker(...catalogHandlers, ...handlers);
