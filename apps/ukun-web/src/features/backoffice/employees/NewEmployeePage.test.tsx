import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { createEmployeeRejects, employeeState, staff } from '../../../test/msw/employeeHandlers';
import { OPERATOR_DETAIL } from '../../../test/msw/problem';
import { server } from '../../../test/msw/server';
import { signedIn } from '../../../test/msw/sessionHandlers';
import { renderWithProviders } from '../../../test/renderWithProviders';
import NewEmployeePage from './NewEmployeePage';

const ADMIN_ROLES = ['EMPLOYEE', 'ADMIN'] as const;

const renderNewEmployee = () =>
  renderWithProviders(<NewEmployeePage />, { route: '/admin/employees/new' });

type Overrides = Partial<{ email: string; password: string }>;

/** Fills the whole form with a valid employee, with room for the field a test wants to break. */
async function fillForm(user: ReturnType<typeof userEvent.setup>, overrides: Overrides = {}) {
  await user.type(await screen.findByLabelText('Correo'), overrides.email ?? 'ana@ukun.dev');
  await user.type(screen.getByLabelText('Contraseña'), overrides.password ?? 'una-frase-larga-y-buena');
  await user.type(screen.getByLabelText('Nombre'), 'Ana');
  await user.type(screen.getByLabelText('Apellido'), 'Pérez');
  await user.type(screen.getByLabelText('Puesto'), 'Vendedora');
}

const submit = (user: ReturnType<typeof userEvent.setup>) =>
  user.click(screen.getByRole('button', { name: 'Dar de alta' }));

describe('NewEmployeePage', () => {
  it('names the store the employee joins, and asks nothing about which one it is', async () => {
    server.use(signedIn([...ADMIN_ROLES]), ...employeeState().handlers);

    renderNewEmployee();

    expect(
      await screen.findByText('Se incorpora a Ukun Gran Vía, que es tu tienda: no hay que elegirla.'),
    ).toBeDefined();
    // There is nothing to pick a store with, because the store is not the browser's to choose
    // (docs/08 §3): the form is five text fields and one checkbox, and no list of anything.
    expect(screen.queryAllByRole('combobox')).toEqual([]);
    expect(screen.queryAllByRole('listbox')).toEqual([]);
  });

  it('creates the employee and sends no store in the body', async () => {
    const user = userEvent.setup();
    const state = employeeState();
    server.use(signedIn([...ADMIN_ROLES]), ...state.handlers);

    renderNewEmployee();
    await fillForm(user);
    await submit(user);

    await waitFor(() => expect(state.counters.created).toBe(1));
    expect(state.bodies[0]).toEqual({
      email: 'ana@ukun.dev',
      password: 'una-frase-larga-y-buena',
      name: 'Ana',
      lastName: 'Pérez',
      jobTitle: 'Vendedora',
      isAdmin: false,
    });
  });

  it('grants the administrator flag only when it was ticked (RF-12)', async () => {
    const user = userEvent.setup();
    const state = employeeState();
    server.use(signedIn([...ADMIN_ROLES]), ...state.handlers);

    renderNewEmployee();
    await fillForm(user);
    await user.click(screen.getByLabelText('Es administrador de la tienda'));
    await submit(user);

    await waitFor(() => expect(state.counters.created).toBe(1));
    expect(state.bodies[0].isAdmin).toBe(true);
  });

  it('says a 202 as the activation pending it is, not as a plain "created"', async () => {
    const user = userEvent.setup();
    const state = employeeState({ activationPending: true });
    server.use(signedIn([...ADMIN_ROLES]), ...state.handlers);

    renderNewEmployee();
    await fillForm(user);
    await submit(user);

    expect(await screen.findByText('Empleado creado, activación pendiente')).toBeDefined();
    // The row exists either way, so the listing is offered rather than taken.
    expect(screen.getByRole('link', { name: 'Ver los empleados' })).toBeDefined();
    expect(state.counters.created).toBe(1);
  });

  it('paints a 409 EMAIL_ALREADY_EXISTS under the email field, and puts the focus there', async () => {
    const user = userEvent.setup();
    const state = employeeState({ employees: staff });
    server.use(signedIn([...ADMIN_ROLES]), ...state.handlers);

    renderNewEmployee();
    await fillForm(user, { email: 'joel@ukun.dev' });
    await submit(user);

    const email = await screen.findByLabelText('Correo');
    expect(await screen.findByText('Ya hay una cuenta con ese correo')).toBeDefined();
    expect(email.getAttribute('aria-describedby')).toContain('error');
    expect(document.activeElement).toBe(email);
    // The operator's English `detail` is never what the reader sees (docs/12 §1).
    expect(screen.queryByText(OPERATOR_DETAIL)).toBeNull();
  });

  it('lands a VALIDATION_FAILED on the field the server named', async () => {
    const user = userEvent.setup();
    // The rejection goes first: MSW resolves with the first handler that matches, so putting it
    // after the state's own `POST` would let the successful one answer.
    server.use(
      signedIn([...ADMIN_ROLES]),
      createEmployeeRejects([{ field: 'jobTitle', message: 'must not be blank' }]),
      ...employeeState().handlers,
    );

    renderNewEmployee();
    await fillForm(user);
    await submit(user);

    expect(await screen.findByText('El puesto son de 1 a 50 caracteres.')).toBeDefined();
    // The API's own `message` is fixed English for the operator and is never painted.
    expect(screen.queryByText('must not be blank')).toBeNull();
  });

  it('refuses a password the contract would reject without asking the server', async () => {
    const user = userEvent.setup();
    const state = employeeState();
    server.use(signedIn([...ADMIN_ROLES]), ...state.handlers);

    renderNewEmployee();
    await fillForm(user, { password: 'corta' });
    await submit(user);

    expect(await screen.findByText('La contraseña tiene al menos 12 caracteres.')).toBeDefined();
    expect(state.counters.created).toBe(0);
  });
});
