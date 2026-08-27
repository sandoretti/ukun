import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { OPERATOR_DETAIL } from '../../../test/msw/problem';
import {
  productTypeState,
  productTypesFail,
  seededTypes,
} from '../../../test/msw/productTypeHandlers';
import { server } from '../../../test/msw/server';
import { renderWithProviders } from '../../../test/renderWithProviders';
import ProductTypesPage from './ProductTypesPage';

const renderProductTypes = () =>
  renderWithProviders(<ProductTypesPage />, { route: '/admin/product-types' });

const rowOf = (name: string) => screen.getByText(name).closest('tr') as HTMLElement;

const dialog = () => screen.getByRole('dialog');

describe('ProductTypesPage', () => {
  it('lists the types with how many products each one has', async () => {
    server.use(...productTypeState({ types: seededTypes }).handlers);

    renderProductTypes();

    await screen.findByText('Sofás');
    expect(within(rowOf('Sofás')).getByText('12')).toBeDefined();
    // Zero is a state and not a figure: it is the only one in which the type can be deleted.
    expect(within(rowOf('Iluminación')).getByText('Sin productos')).toBeDefined();
    expect(screen.getByText('2 tipos')).toBeDefined();
  });

  it('says so when there is no type yet, which is what a fresh catalog looks like', async () => {
    server.use(...productTypeState({ types: [] }).handlers);

    renderProductTypes();

    expect(
      await screen.findByText(
        'Todavía no hay ningún tipo de producto. Crea el primero para poder dar de alta productos.',
      ),
    ).toBeDefined();
  });

  it('paints the failure by its code, never the operator detail the API carries', async () => {
    server.use(productTypesFail('INTERNAL_ERROR', 500));

    renderProductTypes();

    expect(await screen.findByText('Algo ha fallado por nuestra parte. Inténtalo de nuevo')).toBeDefined();
    expect(screen.queryByText(OPERATOR_DETAIL)).toBeNull();
  });

  it('creates a type and shows it without a reload', async () => {
    const user = userEvent.setup();
    const state = productTypeState({ types: seededTypes });
    server.use(...state.handlers);

    renderProductTypes();
    await screen.findByText('Sofás');

    await user.click(screen.getByRole('button', { name: 'Nuevo tipo' }));
    await user.type(within(dialog()).getByLabelText('Nombre'), 'Estanterías');
    await user.click(within(dialog()).getByRole('button', { name: 'Crear tipo' }));

    expect(await screen.findByText('Estanterías')).toBeDefined();
    expect(state.bodies.at(-1)).toEqual({ name: 'Estanterías' });
    expect(screen.getByText('3 tipos')).toBeDefined();
  });

  it('trims the name before it travels: «Sofás » is not a second category', async () => {
    const user = userEvent.setup();
    const state = productTypeState({ types: [] });
    server.use(...state.handlers);

    renderProductTypes();
    await screen.findByRole('button', { name: 'Nuevo tipo' });

    await user.click(screen.getByRole('button', { name: 'Nuevo tipo' }));
    await user.type(within(dialog()).getByLabelText('Nombre'), '  Estanterías  ');
    await user.click(within(dialog()).getByRole('button', { name: 'Crear tipo' }));

    await waitFor(() => expect(state.counters.created).toBe(1));
    expect(state.bodies.at(-1)).toEqual({ name: 'Estanterías' });
  });

  it('paints a name already taken under the name field, not as a form-level error', async () => {
    const user = userEvent.setup();
    server.use(...productTypeState({ types: seededTypes }).handlers);

    renderProductTypes();
    await screen.findByText('Sofás');

    await user.click(screen.getByRole('button', { name: 'Nuevo tipo' }));
    const name = within(dialog()).getByLabelText('Nombre');
    await user.type(name, 'Sofás');
    await user.click(within(dialog()).getByRole('button', { name: 'Crear tipo' }));

    const message = await within(dialog()).findByText('Ya hay otro tipo con ese nombre.');
    // The message is the field's own: it reaches a screen reader through `aria-describedby`, which is
    // what «se rechaza indicando el campo» means in practice (RF-13).
    expect(name.getAttribute('aria-describedby')).toContain(message.id);
    // And the registry's generic sentence, which cannot say which field clashed, is not what is shown.
    expect(within(dialog()).queryByText('Ese valor ya existe. Elige otro')).toBeNull();
  });

  it('renames a type from the row it was opened on', async () => {
    const user = userEvent.setup();
    const state = productTypeState({ types: seededTypes });
    server.use(...state.handlers);

    renderProductTypes();
    await screen.findByText('Sofás');

    await user.click(screen.getByRole('button', { name: 'Renombrar Sofás' }));
    const name = within(dialog()).getByLabelText('Nombre') as HTMLInputElement;
    expect(name.value).toBe('Sofás');

    await user.clear(name);
    await user.type(name, 'Sofás y sillones');
    await user.click(within(dialog()).getByRole('button', { name: 'Guardar' }));

    expect(await screen.findByText('Sofás y sillones')).toBeDefined();
    expect(state.counters.renamed).toBe(1);
  });

  it('refuses to delete a type with products before asking the server', async () => {
    const user = userEvent.setup();
    const state = productTypeState({ types: seededTypes });
    server.use(...state.handlers);

    renderProductTypes();
    await screen.findByText('Sofás');

    await user.click(screen.getByRole('button', { name: 'Borrar Sofás' }));

    expect(within(dialog()).getByText('Este tipo no se puede borrar')).toBeDefined();
    expect(
      within(dialog()).getByText('No se puede eliminar: quedan 12 productos de este tipo'),
    ).toBeDefined();
    // The count the row already carried is the whole answer, so nothing was asked and there is
    // nothing to press: a destructive button guaranteed to fail is not offered.
    expect(within(dialog()).queryByRole('button', { name: 'Borrar tipo' })).toBeNull();
    // What is left to press says what pressing it means, and does not repeat the cross's name.
    expect(within(dialog()).getByRole('button', { name: 'Entendido' })).toBeDefined();
    expect(state.counters.deleted).toBe(0);
  });

  it('deletes a type that has no products', async () => {
    const user = userEvent.setup();
    const state = productTypeState({ types: seededTypes });
    server.use(...state.handlers);

    renderProductTypes();
    await screen.findByText('Iluminación');

    await user.click(screen.getByRole('button', { name: 'Borrar Iluminación' }));
    await user.click(within(dialog()).getByRole('button', { name: 'Borrar tipo' }));

    await waitFor(() => expect(screen.queryByText('Iluminación')).toBeNull());
    expect(state.counters.deleted).toBe(1);
  });

  it('shows the server count when the type gained products between the listing and the button', async () => {
    const user = userEvent.setup();
    server.use(...productTypeState({ types: seededTypes, deletionAlwaysRejected: 4 }).handlers);

    renderProductTypes();
    await screen.findByText('Iluminación');

    await user.click(screen.getByRole('button', { name: 'Borrar Iluminación' }));
    await user.click(within(dialog()).getByRole('button', { name: 'Borrar tipo' }));

    // The figure is the server's `inProducts`, not the `productCount: 0` the row was showing.
    expect(
      await within(dialog()).findByText('No se puede eliminar: quedan 4 productos de este tipo'),
    ).toBeDefined();
    expect(screen.getByText('Iluminación')).toBeDefined();
  });
});
