import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { employeeState, employeesFail, staff, type SeedEmployee } from '../../../test/msw/employeeHandlers';
import { OPERATOR_DETAIL } from '../../../test/msw/problem';
import { server } from '../../../test/msw/server';
import { signedIn } from '../../../test/msw/sessionHandlers';
import { renderWithProviders } from '../../../test/renderWithProviders';
import EmployeesPage from './EmployeesPage';

const ADMIN_ROLES = ['EMPLOYEE', 'ADMIN'] as const;

const renderEmployees = (route = '/admin/employees') =>
  renderWithProviders(<EmployeesPage />, { route });

const rowOf = (name: string) => screen.getByText(name).closest('tr') as HTMLElement;

/** Enough employees to fill two pages of twenty, so the pagination has somewhere to go. */
const twentyFive: SeedEmployee[] = Array.from({ length: 25 }, (_, index) => ({
  id: 100 + index,
  name: `Empleado${index}`,
  lastName: 'Ficticio',
  email: `empleado${index}@ukun.dev`,
  jobTitle: 'Vendedor',
  isAdmin: false,
}));

describe('EmployeesPage', () => {
  it('names the store the admin works in, resolved from the session and never from the URL', async () => {
    server.use(signedIn([...ADMIN_ROLES]), ...employeeState({ employees: staff }).handlers);

    renderEmployees();

    expect(
      await screen.findByRole('heading', { name: 'Empleados de Ukun Gran Vía', level: 1 }),
    ).toBeDefined();
  });

  it('lists the staff with their job title, and marks the administrators', async () => {
    server.use(signedIn([...ADMIN_ROLES]), ...employeeState({ employees: staff }).handlers);

    renderEmployees();

    await screen.findByText('Sandoval');
    expect(within(rowOf('Joel')).getByText('Gerente')).toBeDefined();
    expect(within(rowOf('Joel')).getByText('Administrador')).toBeDefined();
    expect(within(rowOf('Daniel')).getByText('No es administrador')).toBeDefined();
    expect(screen.getByText('3 empleados')).toBeDefined();
  });

  it('sends the text filter to the server instead of narrowing the table in the browser', async () => {
    const user = userEvent.setup();
    const state = employeeState({ employees: staff });
    server.use(signedIn([...ADMIN_ROLES]), ...state.handlers);

    renderEmployees();
    await screen.findByText('Sandoval');

    await user.type(screen.getByLabelText('Buscar empleados'), 'garcia');
    await user.tab();

    await waitFor(() => expect(screen.queryByText('Sandoval')).toBeNull());
    expect(state.searches.at(-1)).toContain('q=garcia');
    // The count is the server's `totalElements`, which a browser-side filter could not produce.
    expect(screen.getByText('1 empleado')).toBeDefined();
  });

  it('asks the server for each of the three states of the administrator filter', async () => {
    const user = userEvent.setup();
    const state = employeeState({ employees: staff });
    server.use(signedIn([...ADMIN_ROLES]), ...state.handlers);

    renderEmployees();
    await screen.findByText('Sandoval');

    const filter = screen.getByLabelText('Filtrar por administrador');

    await user.selectOptions(filter, 'Solo administradores');
    await waitFor(() => expect(screen.queryByText('García')).toBeNull());
    expect(state.searches.at(-1)).toContain('isAdmin=true');

    await user.selectOptions(filter, 'Solo empleados');
    await waitFor(() => expect(screen.queryByText('Sandoval')).toBeNull());
    expect(state.searches.at(-1)).toContain('isAdmin=false');

    // "Everybody" is the absence of the parameter, not `isAdmin=`: an empty value is one the server
    // would have to reject.
    await user.selectOptions(filter, 'Todos');
    await screen.findByText('Sandoval');
    expect(state.searches.at(-1)).not.toContain('isAdmin');
  });

  it('reads the filters from the URL: a filtered table is linkable', async () => {
    const state = employeeState({ employees: staff });
    server.use(signedIn([...ADMIN_ROLES]), ...state.handlers);

    renderEmployees('/admin/employees?q=garcia&isAdmin=false');

    await screen.findByText('García');
    expect(screen.queryByText('Sandoval')).toBeNull();
    expect(state.searches[0]).toContain('q=garcia');
    expect(state.searches[0]).toContain('isAdmin=false');
  });

  it('pages, counting from one in the URL and from zero towards the API', async () => {
    const user = userEvent.setup();
    const state = employeeState({ employees: twentyFive });
    server.use(signedIn([...ADMIN_ROLES]), ...state.handlers);

    renderEmployees();
    await screen.findByText('Empleado0');
    expect(state.searches[0]).toContain('page=0');

    await user.click(screen.getByRole('button', { name: 'Siguiente' }));

    expect(await screen.findByText('Empleado20')).toBeDefined();
    expect(state.searches.at(-1)).toContain('page=1');
  });

  it('tells "this store has nobody else" apart from "the search found nothing"', async () => {
    server.use(signedIn([...ADMIN_ROLES]), ...employeeState({ employees: [] }).handlers);

    const { unmount } = renderEmployees();

    expect(
      await screen.findByText('En esta tienda no hay más empleados que tú.'),
    ).toBeDefined();
    unmount();

    server.use(signedIn([...ADMIN_ROLES]), ...employeeState({ employees: staff }).handlers);

    renderEmployees('/admin/employees?q=nadie');

    expect(await screen.findByText('Ningún empleado coincide con la búsqueda.')).toBeDefined();
  });

  it('paints a failed read as its translated code, never as the operator detail', async () => {
    server.use(signedIn([...ADMIN_ROLES]), employeesFail('INTERNAL_ERROR', 500));

    renderEmployees();

    expect(await screen.findByText('Algo ha fallado por nuestra parte. Inténtalo de nuevo')).toBeDefined();
    expect(screen.queryByText(OPERATOR_DETAIL)).toBeNull();
  });

  it('puts the filters in one named search region, with what is searched said under it', async () => {
    server.use(signedIn([...ADMIN_ROLES]), ...employeeState({ employees: staff }).handlers);

    renderEmployees();
    await screen.findByText('Sandoval');

    // The bar names itself for assistive technology instead of carrying a visible "Filter" heading.
    const filters = screen.getByRole('search', { name: 'Filtros de empleados' });
    expect(within(filters).getByLabelText('Buscar empleados')).toBeDefined();
    expect(within(filters).getByLabelText('Filtrar por administrador')).toBeDefined();
    expect(
      screen.getByText('Se busca por nombre, apellido o correo, y basta con una parte.'),
    ).toBeDefined();
  });

  it('offers to clear only once something is filtered, and counts what it would clear', async () => {
    const user = userEvent.setup();
    server.use(signedIn([...ADMIN_ROLES]), ...employeeState({ employees: staff }).handlers);

    renderEmployees();
    await screen.findByText('Sandoval');
    expect(screen.queryByRole('button', { name: /Quitar/ })).toBeNull();

    await user.type(screen.getByLabelText('Buscar empleados'), 'gar');
    await user.tab();

    expect(await screen.findByRole('button', { name: 'Quitar el filtro' })).toBeDefined();

    await user.selectOptions(screen.getByLabelText('Filtrar por administrador'), 'Solo empleados');

    const clear = await screen.findByRole('button', { name: 'Quitar los 2 filtros' });
    await user.click(clear);

    // Clearing empties the controls and takes the filters out of the URL, both at once.
    await screen.findByText('Sandoval');
    expect(screen.queryByRole('button', { name: /Quitar/ })).toBeNull();
    expect((screen.getByLabelText('Buscar empleados') as HTMLInputElement).value).toBe('');
  });

  it('paints a 403 as the denial it is, and not as a screen that never finished loading', async () => {
    // The account has the role the route asks for and the server still refuses the read — a token
    // that lost its claim, an employee moved out of the store. It is an answer, so it is painted as
    // one: the guard is not the barrier and this is where that shows (docs/08 §4).
    server.use(signedIn([...ADMIN_ROLES]), employeesFail('FORBIDDEN', 403));

    renderEmployees();

    expect(await screen.findByText('Tu cuenta no tiene acceso a esto')).toBeDefined();
    expect(screen.queryByText(OPERATOR_DETAIL)).toBeNull();
  });

  it('offers no way to edit or delete an employee, because neither exists (RF-11)', async () => {
    server.use(signedIn([...ADMIN_ROLES]), ...employeeState({ employees: staff }).handlers);

    renderEmployees();
    await screen.findByText('Sandoval');

    // Not "disabled": absent. No row carries a control of any kind...
    for (const employee of ['Joel', 'Daniel', 'Lucía']) {
      expect(within(rowOf(employee)).queryAllByRole('button')).toEqual([]);
      expect(within(rowOf(employee)).queryAllByRole('link')).toEqual([]);
    }

    // ...and the only action the whole screen offers is the alta.
    expect(screen.getAllByRole('link')).toHaveLength(1);
    expect(screen.getByRole('link', { name: /Dar de alta empleado/ })).toBeDefined();
  });
});
