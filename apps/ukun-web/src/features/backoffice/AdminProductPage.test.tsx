import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import {
  adminProduct,
  adminProductFails,
  adminProductState,
  uploadImageFails,
} from '../../test/msw/adminProductHandlers';
import { OPERATOR_DETAIL } from '../../test/msw/problem';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/renderWithProviders';
import AdminProductPage from './AdminProductPage';

const sofa = adminProduct({
  id: 1,
  sku: 'SOFA-CUERO-01',
  name: 'Sofá de cuero',
  description: 'Tres plazas.',
  price: '399.99',
  typeId: 1,
});

const renderSheet = (id = 1) =>
  renderWithProviders(
    <Routes>
      <Route path="/admin/products/:id" element={<AdminProductPage />} />
      {/* Where deleting lands: a marker for the route, not a screen. */}
      <Route path="/admin/products" element={<p data-testid="catalog" />} />
    </Routes>,
    { route: `/admin/products/${id}` },
  );

/** A real file of the size it says, for the ones that actually travel. */
function imageFile(name: string, type: string, bytes: number) {
  return new File(['x'.repeat(bytes)], name, { type });
}

/**
 * A file that only claims to weigh what it says. It is for the size check, which happens in the
 * browser and never sends anything — allocating six real megabytes to prove a comparison would be
 * paying for the test with the run's memory.
 */
function oversizedFile(name: string, type: string, bytes: number) {
  const file = new File(['x'], name, { type });

  Object.defineProperty(file, 'size', { value: bytes });

  return file;
}

const pickFile = (user: ReturnType<typeof userEvent.setup>, file: File) =>
  user.upload(screen.getByLabelText('Elegir una foto'), file);

/**
 * A reader whose browser hands over whatever was picked, `accept` or no `accept`.
 *
 * The attribute is a hint the file dialog takes, not a rule: a drag-and-drop, or a browser that
 * ignores it, both deliver the file anyway — and refusing it there is precisely what the panel is
 * being tested for. `userEvent` enforces `accept` by default, which would make the check untestable
 * by hiding the case it exists for.
 */
const pickerIgnoringAccept = () => userEvent.setup({ applyAccept: false });

describe('AdminProductPage', () => {
  it('shows the SKU as a value and never as a field: it is fixed at creation', async () => {
    server.use(...adminProductState({ products: [sofa] }).handlers);

    renderSheet();

    await screen.findByRole('heading', { name: 'Sofá de cuero', level: 1 });
    expect(screen.getAllByText('SOFA-CUERO-01').length).toBeGreaterThan(0);
    expect(screen.queryByRole('textbox', { name: 'SKU' })).toBeNull();
  });

  it('keeps the catalog status out of the form: it is a decision of its own, not a checkbox', async () => {
    server.use(...adminProductState({ products: [sofa] }).handlers);

    renderSheet();

    await screen.findByLabelText('Nombre');
    expect(screen.queryByRole('checkbox')).toBeNull();
    expect(screen.getByRole('button', { name: 'Descatalogar' })).toBeDefined();
  });

  it('saves the four fields an admin edits, and nothing else', async () => {
    const user = userEvent.setup();
    const state = adminProductState({ products: [sofa] });
    server.use(...state.handlers);

    renderSheet();

    const name = await screen.findByLabelText('Nombre');
    await user.clear(name);
    await user.type(name, 'Sofá de cuero curtido');
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByText('Cambios guardados.')).toBeDefined();
    expect(state.bodies[0]).toEqual({
      name: 'Sofá de cuero curtido',
      description: 'Tres plazas.',
      price: '399.99',
      typeId: 1,
    });
  });

  it('shows the type the product already has, and not an empty picker', async () => {
    server.use(...adminProductState({ products: [sofa] }).handlers);

    renderSheet();

    // The closed list arrives after the product does. A control that ignores that shows «pick a type»
    // over a product that has one, and the first save would look like a change nobody made.
    const type = (await screen.findByLabelText('Tipo')) as HTMLSelectElement;
    await waitFor(() => expect(type.value).toBe(String(sofa.typeId)));
  });

  it('says whether there is anything to save, and takes it back', async () => {
    const user = userEvent.setup();
    const state = adminProductState({ products: [sofa] });
    server.use(...state.handlers);

    renderSheet();

    const name = await screen.findByLabelText('Nombre');
    expect(screen.getByText('Sin cambios')).toBeDefined();

    await user.type(name, ' curtido');
    expect(await screen.findByText('Sin guardar')).toBeDefined();

    await user.click(screen.getByRole('button', { name: 'Descartar' }));

    // Back to the product as the server describes it, and nothing was sent to say so.
    expect((name as HTMLInputElement).value).toBe('Sofá de cuero');
    expect(await screen.findByText('Sin cambios')).toBeDefined();
    expect(state.bodies).toEqual([]);
  });

  it('discontinues without deleting, and offers the way back', async () => {
    const user = userEvent.setup();
    const state = adminProductState({ products: [sofa] });
    server.use(...state.handlers);

    renderSheet();

    await user.click(await screen.findByRole('button', { name: 'Descatalogar' }));

    expect(await screen.findByRole('button', { name: 'Reactivar' })).toBeDefined();
    // The product is still there: discontinuing is not deleting.
    expect(state.rows).toHaveLength(1);
    expect(state.rows[0].isActive).toBe(false);
  });

  it('resolves a discontinued product, which is the only way one can be reactivated', async () => {
    const user = userEvent.setup();
    const state = adminProductState({ products: [{ ...sofa, isActive: false }] });
    server.use(...state.handlers);

    renderSheet();

    expect(await screen.findByText('Descatalogado')).toBeDefined();
    await user.click(screen.getByRole('button', { name: 'Reactivar' }));

    await waitFor(() => expect(state.rows[0].isActive).toBe(true));
  });

  it('deletes a product nothing points at, and goes back to the catalog', async () => {
    const user = userEvent.setup();
    const state = adminProductState({ products: [sofa] });
    server.use(...state.handlers);

    renderSheet();

    await user.click(await screen.findByRole('button', { name: 'Borrar producto' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Borrar del todo' }));

    expect(await screen.findByTestId('catalog')).toBeDefined();
    expect(state.rows).toHaveLength(0);
  });

  it('says where a referenced product is used, and offers discontinuing instead', async () => {
    const user = userEvent.setup();
    const state = adminProductState({
      products: [{ ...sofa, references: { inOrders: 3, inStock: 2, inCarts: 1 } }],
    });
    server.use(...state.handlers);

    renderSheet();

    await user.click(await screen.findByRole('button', { name: 'Borrar producto' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Borrar del todo' }));

    // The sentence of the closed registry, with the extensions of the problem in it.
    expect(
      await screen.findByText(
        'No se puede eliminar: el producto está en 3 pedidos, 2 tiendas y 1 carritos',
      ),
    ).toBeDefined();
    expect(screen.queryByText(OPERATOR_DETAIL)).toBeNull();

    await user.click(within(dialog).getByRole('button', { name: 'Descatalogar en su lugar' }));

    await waitFor(() => expect(state.rows[0].isActive).toBe(false));
    expect(state.rows).toHaveLength(1);
  });

  it('previews the picked photo before anything is sent, and then uploads it', async () => {
    const user = userEvent.setup();
    const state = adminProductState({ products: [sofa] });
    server.use(...state.handlers);

    renderSheet();

    await screen.findByLabelText('Elegir una foto');
    await pickFile(user, imageFile('sofa.jpg', 'image/jpeg', 1024));

    expect(await screen.findByAltText('Vista previa de la foto elegida')).toBeDefined();
    // Nothing has travelled yet: a preview is what the browser draws, not what the server stored.
    expect(state.uploads).toEqual([]);

    await user.click(screen.getByRole('button', { name: 'Subir foto' }));

    // One multipart request, with the picked image in the part the contract names `file`.
    await waitFor(() => expect(state.uploads).toEqual([{ type: 'image/jpeg' }]));
    expect(state.rows[0].imageUrl).toBe('/media/products/1.webp');
  });

  it('refuses a file the contract would not take, without sending it', async () => {
    const user = pickerIgnoringAccept();
    const state = adminProductState({ products: [sofa] });
    server.use(...state.handlers);

    renderSheet();
    await screen.findByLabelText('Elegir una foto');

    await pickFile(user, imageFile('manual.pdf', 'application/pdf', 1024));
    expect(await screen.findByText('Ese fichero no es JPEG, PNG ni WebP.')).toBeDefined();

    await pickFile(user, oversizedFile('enorme.jpg', 'image/jpeg', 6 * 1024 * 1024));
    expect(await screen.findByText('Ese fichero pasa de 5 MB.')).toBeDefined();

    expect(state.uploads).toEqual([]);
  });

  it('paints the server refusing a file by its code: its check is the one that decides', async () => {
    const user = userEvent.setup();
    server.use(uploadImageFails('UNSUPPORTED_MEDIA_TYPE', 415), ...adminProductState({ products: [sofa] }).handlers);

    renderSheet();
    await screen.findByLabelText('Elegir una foto');

    // A JPEG as far as the browser is concerned; the server looks at what is really inside.
    await pickFile(user, imageFile('sofa.jpg', 'image/jpeg', 1024));
    await user.click(screen.getByRole('button', { name: 'Subir foto' }));

    expect(await screen.findByText('Ese tipo de fichero no se admite')).toBeDefined();
  });

  it('removes the photo of a product that has one', async () => {
    const user = userEvent.setup();
    const state = adminProductState({ products: [{ ...sofa, imageUrl: '/media/products/1.jpg' }] });
    server.use(...state.handlers);

    renderSheet();

    await user.click(await screen.findByRole('button', { name: 'Quitar foto' }));

    await waitFor(() => expect(state.rows[0].imageUrl).toBeNull());
    expect(screen.queryByRole('button', { name: 'Quitar foto' })).toBeNull();
  });

  it('paints a failed read by its code, never with the detail written for the operator', async () => {
    server.use(adminProductFails('RESOURCE_NOT_FOUND', 404));

    renderSheet(999);

    expect(await screen.findByText('No encontramos lo que buscas')).toBeDefined();
    expect(screen.queryByText(OPERATOR_DETAIL)).toBeNull();
  });
});
