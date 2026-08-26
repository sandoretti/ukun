import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { OPERATOR_DETAIL } from '../../test/msw/problem';
import { server } from '../../test/msw/server';
import { signedIn } from '../../test/msw/sessionHandlers';
import { employmentFails, stockFails, stockState, updateStockFails } from '../../test/msw/stockHandlers';
import { renderWithProviders } from '../../test/renderWithProviders';
import StockPage from './StockPage';

/** Two lines of the seeded catalog: `SOFA-CUERO-01` and `MESA-COMEDOR-01`. */
const twoLines = [
  { lineId: 10, productId: 1, quantity: 4, version: 0 },
  { lineId: 11, productId: 3, quantity: 7, version: 3 },
];

const SOFA = 'Sofá de cuero';
const TABLE = 'Mesa de comedor';

const renderStock = (route = '/backoffice/stock') => renderWithProviders(<StockPage />, { route });

const rowOf = (product: string) => screen.getByText(product).closest('tr') as HTMLElement;

const editButton = (product: string) =>
  screen.getByRole('button', { name: `Cambiar las unidades de ${product}` });

const deleteButton = (product: string) =>
  screen.getByRole('button', { name: `Quitar ${product} del stock` });

/** Opens the quantity dialog of a line and hands it back, already on screen. */
async function openEditor(user: ReturnType<typeof userEvent.setup>, product: string) {
  await user.click(editButton(product));

  return screen.findByRole('dialog');
}

async function typeQuantity(user: ReturnType<typeof userEvent.setup>, dialog: HTMLElement, value: string) {
  const field = within(dialog).getByLabelText('Unidades');

  await user.clear(field);
  await user.type(field, value);
}

describe('StockPage', () => {
  it('names the store the employee works in, resolved from the session and never from the URL', async () => {
    server.use(signedIn(['EMPLOYEE']), ...stockState({ lines: twoLines }).handlers);

    renderStock();

    expect(
      await screen.findByRole('heading', { name: 'Stock de Ukun Gran Vía', level: 1 }),
    ).toBeDefined();
  });

  it('lists the stock with the quantities the server answered with', async () => {
    server.use(signedIn(['EMPLOYEE']), ...stockState({ lines: twoLines }).handlers);

    renderStock();

    await screen.findByText(SOFA);
    expect(within(rowOf(SOFA)).getByText('4')).toBeDefined();
    expect(within(rowOf(TABLE)).getByText('7')).toBeDefined();
    expect(screen.getByText('2 productos')).toBeDefined();
  });

  it('lists the products with no row in this store on the other tab, each linking to its own add screen', async () => {
    const user = userEvent.setup();
    server.use(signedIn(['EMPLOYEE']), ...stockState({ lines: twoLines }).handlers);

    renderStock();
    await screen.findByText(SOFA);

    await user.selectOptions(screen.getByLabelText('Qué se lista'), 'Sin dar de alta');

    // The sofa has a row, so it is not a candidate; the bunk bed has none.
    const candidate = await screen.findByText('Litera infantil');
    expect(within(candidate.closest('tr') as HTMLElement).getByRole('link').getAttribute('href')).toBe(
      '/backoffice/stock/new?sku=LITERA-INF-01',
    );
    expect(screen.queryByText(SOFA)).toBeNull();
  });

  it('sends the SKU filter to the server instead of narrowing the table in the browser', async () => {
    const user = userEvent.setup();
    const state = stockState({ lines: twoLines });
    server.use(signedIn(['EMPLOYEE']), ...state.handlers);

    renderStock();
    await screen.findByText(SOFA);

    await user.type(screen.getByLabelText('SKU'), 'SOFA-CUERO-01');
    await user.tab();

    await waitFor(() => expect(screen.queryByText(TABLE)).toBeNull());
    expect(state.searches.at(-1)).toContain('sku=SOFA-CUERO-01');
    // The count is the server's `totalElements`, which a browser-side filter could not produce.
    expect(screen.getByText('1 producto')).toBeDefined();
  });

  it('reads the tab and the filter from the URL: a filtered table is linkable', async () => {
    const state = stockState({ lines: twoLines });
    server.use(signedIn(['EMPLOYEE']), ...state.handlers);

    renderStock('/backoffice/stock?sku=MESA-COMEDOR-01');

    await screen.findByText(TABLE);
    expect(screen.queryByText(SOFA)).toBeNull();
    expect(state.searches[0]).toContain('sku=MESA-COMEDOR-01');
  });

  it('tells "this store holds nothing" apart from "the search found nothing"', async () => {
    server.use(signedIn(['EMPLOYEE']), ...stockState({ lines: [] }).handlers);

    const { unmount } = renderStock();

    expect(
      await screen.findByText('Esta tienda todavía no tiene ningún producto en stock.'),
    ).toBeDefined();
    unmount();

    renderStock('/backoffice/stock?sku=LAMPARA-PIE-01');

    expect(
      await screen.findByText('Ningún producto coincide con la búsqueda.'),
    ).toBeDefined();
  });

  it('changes a quantity and shows the one the server answered with', async () => {
    const user = userEvent.setup();
    const state = stockState({ lines: twoLines });
    server.use(signedIn(['EMPLOYEE']), ...state.handlers);

    renderStock();
    await screen.findByText(SOFA);

    const dialog = await openEditor(user, SOFA);
    await typeQuantity(user, dialog, '9');
    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    await waitFor(() => expect(within(rowOf(SOFA)).getByText('9')).toBeDefined());
    expect(state.counters.updated).toBe(1);
  });

  it('shows both halves of the store in one table, counting them together', async () => {
    const user = userEvent.setup();
    const state = stockState({ lines: twoLines });
    server.use(signedIn(['EMPLOYEE']), ...state.handlers);

    renderStock();
    await screen.findByText(SOFA);

    await user.selectOptions(screen.getByLabelText('Qué se lista'), 'Todo');

    // The sofa has a line and the bunk bed has none: in this view they are rows of the same table.
    expect(await screen.findByText('Litera infantil')).toBeDefined();
    expect(screen.getByText(SOFA)).toBeDefined();

    // Two requests, one per half, and the count is their sum — which is what the store really holds
    // plus what it could.
    expect(state.searches.at(-2)).toContain('status=STOCKED');
    expect(state.searches.at(-1)).toContain('status=NOT_STOCKED');
  });

  it('tells "no units left" apart from "not added yet", which are different things (RF-09)', async () => {
    const user = userEvent.setup();
    server.use(
      signedIn(['EMPLOYEE']),
      ...stockState({ lines: [{ lineId: 10, productId: 1, quantity: 0, version: 0 }] }).handlers,
    );

    renderStock();
    await screen.findByText(SOFA);

    await user.selectOptions(screen.getByLabelText('Qué se lista'), 'Todo');
    await screen.findByText('Litera infantil');

    // The sofa has a line and no units; the bunk bed has no line at all.
    expect(within(rowOf(SOFA)).getByText('Sin unidades')).toBeDefined();
    expect(within(rowOf('Litera infantil')).getByText('Sin dar de alta')).toBeDefined();
  });

  it('keeps the view in the URL, so a screen showing both halves is linkable', async () => {
    const user = userEvent.setup();
    const state = stockState({ lines: twoLines });
    server.use(signedIn(['EMPLOYEE']), ...state.handlers);

    // Arriving with the view already chosen: it is read back into the control, not defaulted away.
    const { unmount } = renderStock('/backoffice/stock?status=ALL');

    await screen.findByText('Litera infantil');
    // `.value` and not `getAttribute`: a `<select>` has no value attribute, only a property, and
    // asserting on the attribute would pass against any option at all.
    expect((await screen.findByLabelText('Qué se lista')).getAttribute('value')).toBeNull();
    expect((screen.getByLabelText('Qué se lista') as HTMLSelectElement).value).toBe('ALL');
    unmount();

    // And choosing it writes it: the three views are one control with three values, and the URL is
    // what holds the answer.
    renderStock();
    await screen.findByText(SOFA);
    await user.selectOptions(screen.getByLabelText('Qué se lista'), 'Sin dar de alta');

    await waitFor(() => expect(state.searches.at(-1)).toContain('status=NOT_STOCKED'));
  });

  it('accepts zero, which is a line that exists with none left', async () => {
    const user = userEvent.setup();
    const state = stockState({ lines: twoLines });
    server.use(signedIn(['EMPLOYEE']), ...state.handlers);

    renderStock();
    await screen.findByText(SOFA);

    const dialog = await openEditor(user, SOFA);
    await typeQuantity(user, dialog, '0');
    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }));

    await waitFor(() => expect(state.counters.updated).toBe(1));
    // Zero is drawn as a badge and not as a digit: a line that exists with none left is a state, and
    // the crossed view puts it next to rows that have no line at all.
    await waitFor(() => expect(within(rowOf(SOFA)).getByText('Sin unidades')).toBeDefined());
  });

  it('rejects a negative quantity before asking the server', async () => {
    const user = userEvent.setup();
    const state = stockState({ lines: twoLines });
    server.use(signedIn(['EMPLOYEE']), ...state.handlers);

    renderStock();
    await screen.findByText(SOFA);

    const dialog = await openEditor(user, SOFA);
    await typeQuantity(user, dialog, '-5');
    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }));

    expect(
      await within(dialog).findByText('Las unidades son un número entero de 0 o más.'),
    ).toBeDefined();
    expect(state.counters.updated).toBe(0);
  });

  it('does not swallow a version conflict: it shows both figures and asks', async () => {
    const user = userEvent.setup();
    const state = stockState({ lines: twoLines });
    server.use(signedIn(['EMPLOYEE']), ...state.handlers);

    renderStock();
    await screen.findByText(SOFA);

    const dialog = await openEditor(user, SOFA);
    // The other employee, between the read and the write.
    state.bumpQuantity(10, 9);

    await typeQuantity(user, dialog, '7');
    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }));

    expect(await within(dialog).findByText('Alguien ha cambiado esta línea')).toBeDefined();
    expect(await within(dialog).findByRole('button', { name: 'Quedarme con 9' })).toBeDefined();
    expect(within(dialog).getByRole('button', { name: 'Aplicar mis 7' })).toBeDefined();
    // Nothing was written and nothing was retried on its own.
    expect(state.counters.updated).toBe(0);
  });

  it('does not tell the reader to reload what it has already reloaded', async () => {
    const user = userEvent.setup();
    const state = stockState({ lines: twoLines });
    server.use(signedIn(['EMPLOYEE']), ...state.handlers);

    renderStock();
    await screen.findByText(SOFA);

    const dialog = await openEditor(user, SOFA);
    state.bumpQuantity(10, 9);
    await typeQuantity(user, dialog, '7');
    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }));

    await within(dialog).findByText('Alguien ha cambiado esta línea');
    // `errors:VERSION_CONFLICT` says "reload it", and by now the listing has been re-read.
    expect(screen.queryByText(/Vuelve a cargarlo/)).toBeNull();
  });

  it('keeps the other employee’s figure when that is what is chosen, without writing anything', async () => {
    const user = userEvent.setup();
    const state = stockState({ lines: twoLines });
    server.use(signedIn(['EMPLOYEE']), ...state.handlers);

    renderStock();
    await screen.findByText(SOFA);

    const dialog = await openEditor(user, SOFA);
    state.bumpQuantity(10, 9);
    await typeQuantity(user, dialog, '7');
    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }));

    await user.click(await within(dialog).findByRole('button', { name: 'Quedarme con 9' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(within(rowOf(SOFA)).getByText('9')).toBeDefined();
    expect(state.counters.updated).toBe(0);
  });

  it('reapplies your own figure with the version the server just sent, never with the stale one', async () => {
    const user = userEvent.setup();
    const state = stockState({ lines: twoLines });
    server.use(signedIn(['EMPLOYEE']), ...state.handlers);

    renderStock();
    await screen.findByText(SOFA);

    const dialog = await openEditor(user, SOFA);
    state.bumpQuantity(10, 9);
    await typeQuantity(user, dialog, '7');
    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }));

    await user.click(await within(dialog).findByRole('button', { name: 'Aplicar mis 7' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    await waitFor(() => expect(within(rowOf(SOFA)).getByText('7')).toBeDefined());
    // The first attempt carried the version that was read, the retry the one the conflict revealed.
    expect(state.versionsSeen).toEqual([0, 1]);
  });

  it('says so when the line is gone instead of pretending there is still something to save', async () => {
    const user = userEvent.setup();
    const state = stockState({ lines: twoLines });
    server.use(signedIn(['EMPLOYEE']), ...state.handlers);

    renderStock();
    await screen.findByText(SOFA);

    const dialog = await openEditor(user, SOFA);
    // The other employee removed it from the stock while this dialog was open.
    state.removeLine(10);

    await typeQuantity(user, dialog, '9');
    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }));

    expect(await within(dialog).findByText(/Esa línea ya no existe/)).toBeDefined();
  });

  it('asks before deleting, and says it is not the same as setting the quantity to zero', async () => {
    const user = userEvent.setup();
    const state = stockState({ lines: twoLines });
    server.use(signedIn(['EMPLOYEE']), ...state.handlers);

    renderStock();
    await screen.findByText(SOFA);

    await user.click(deleteButton(SOFA));

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(/No es lo mismo que poner las unidades a cero/)).toBeDefined();
    expect(state.counters.deleted).toBe(0);
  });

  it('deletes the line and leaves the rest of the listing standing', async () => {
    const user = userEvent.setup();
    const state = stockState({ lines: twoLines });
    server.use(signedIn(['EMPLOYEE']), ...state.handlers);

    renderStock();
    await screen.findByText(SOFA);

    await user.click(deleteButton(SOFA));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Quitar del stock' }));

    await waitFor(() => expect(screen.queryByText(SOFA)).toBeNull());
    expect(screen.getByText(TABLE)).toBeDefined();
    expect(state.counters.deleted).toBe(1);
  });

  it('paints a failed read by its code, never the detail written for the operator', async () => {
    server.use(signedIn(['EMPLOYEE']), stockFails('FORBIDDEN', 403));

    renderStock();

    expect(await screen.findByText('Tu cuenta no tiene acceso a esto')).toBeDefined();
    expect(screen.queryByText(OPERATOR_DETAIL)).toBeNull();
  });

  it('reports a failed write without wiping the table that is already on screen', async () => {
    const user = userEvent.setup();
    // The failing `PUT` goes first: `server.use` keeps the order it is given, and the state's own
    // handler would answer before it otherwise.
    server.use(
      updateStockFails('INTERNAL_ERROR', 500),
      signedIn(['EMPLOYEE']),
      ...stockState({ lines: twoLines }).handlers,
    );

    renderStock();
    await screen.findByText(SOFA);

    const dialog = await openEditor(user, SOFA);
    await typeQuantity(user, dialog, '9');
    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }));

    expect(await within(dialog).findByText(/Algo ha fallado por nuestra parte/)).toBeDefined();
    expect(within(dialog).queryByText(OPERATOR_DETAIL)).toBeNull();
    // The failed write announced itself; it did not take the listing down with it.
    expect(screen.getByText(TABLE)).toBeDefined();
  });

  it('does not offer pagination while everything fits on one page', async () => {
    server.use(signedIn(['EMPLOYEE']), ...stockState({ lines: twoLines }).handlers);

    renderStock();
    await screen.findByText(SOFA);

    expect(screen.queryByRole('navigation', { name: 'Paginación del stock' })).toBeNull();
  });

  it('asks the API for the page before the one the URL shows', async () => {
    const state = stockState({ lines: twoLines });
    server.use(signedIn(['EMPLOYEE']), ...state.handlers);

    renderStock('/backoffice/stock?status=NOT_STOCKED&page=2');

    await screen.findByRole('navigation', { name: 'Paginación del stock' });
    // The URL counts from one and the API from zero.
    expect(state.searches[0]).toContain('page=1');
  });

  it('explains itself to an account that manages no store instead of showing an empty table', async () => {
    server.use(signedIn(['EMPLOYEE']), employmentFails('FORBIDDEN', 403));

    renderStock();

    expect(
      await screen.findByText('Tu cuenta no está asignada a ninguna tienda.'),
    ).toBeDefined();
  });
});
