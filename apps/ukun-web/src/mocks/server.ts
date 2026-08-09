import { setupServer } from 'msw/node';
import { catalogHandlers } from './catalog';
import { handlers } from './handlers';

// Tests run against the very same handlers as `npm run dev` (docs/13-testing.md §2): a second set of
// mocks kept apart would drift and stop saying anything about what the app actually talks to.
export const server = setupServer(...catalogHandlers, ...handlers);
