import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http as mswHttp, HttpResponse } from 'msw';
import { afterEach, describe, expect, it } from 'vitest';

import i18n from '../../i18n/config';
import { server } from '../../test/msw/server';
import { customerProfile, profileFails, profileValidationFailed } from '../../test/msw/profileHandlers';
import { currentUser } from '../../test/msw/sessionHandlers';
import { renderWithProviders } from '../../test/renderWithProviders';
import { useCurrentUser } from '../../shared/api/session';
import type { components } from '../../types/api';
import ProfilePage from './ProfilePage';

type UpdateProfileRequest = components['schemas']['UpdateProfileRequest'];

// The i18n instance is shared by the whole suite, so a test that saves a new language puts it back.
afterEach(async () => {
  await i18n.changeLanguage('es');
});

/** Reads the session the way the shell does, so its refetches can be counted from outside. */
function SessionProbe() {
  const { data } = useCurrentUser();
  return <span>{data?.email ?? ''}</span>;
}

/**
 * The session probe, answering as a customer and leaving a mark on every read: how many times
 * `GET /api/me` was asked for is the observable proof that its cache entry was invalidated, and it
 * beats reaching into the query client.
 */
function signedInCounting(reads: string[]) {
  return mswHttp.get('/api/me', () => {
    reads.push('read');
    return HttpResponse.json({ ...currentUser, locale: customerProfile.locale });
  });
}

/** Captures what the screen actually sent to `PUT /api/me/profile`. */
function capturingSave(sent: UpdateProfileRequest[]) {
  return mswHttp.put('/api/me/profile', async ({ request }) => {
    const body = (await request.json()) as UpdateProfileRequest;
    sent.push(body);
    return HttpResponse.json({ ...body, email: customerProfile.email });
  });
}

const field = (label: string) => screen.getByLabelText(label);

const save = () => screen.getByRole('button', { name: 'Guardar cambios' });

async function renderProfile() {
  renderWithProviders(<ProfilePage />, { route: '/profile' });
  // The form is only there once the profile has arrived; every test starts from that point.
  await screen.findByRole('button', { name: 'Guardar cambios' });
}

describe('ProfilePage', () => {
  it('announces the wait and then shows the profile in its fields (RF-05)', async () => {
    renderWithProviders(<ProfilePage />, { route: '/profile' });

    expect(screen.getByRole('status', { name: 'Cargando...' })).toBeDefined();

    expect(await screen.findByLabelText('Nombre')).toHaveProperty('value', 'Ana');
    expect(field('Apellidos')).toHaveProperty('value', 'García');
    expect(field('Calle y número')).toHaveProperty('value', 'Calle Uría 1');
    expect(field('Ciudad')).toHaveProperty('value', 'Oviedo');
    expect(field('Provincia')).toHaveProperty('value', 'Asturias');
    expect(field('Código postal')).toHaveProperty('value', '33003');
  });

  it('shows the card as a card, and says it in one sentence for whoever cannot see it', async () => {
    await renderProfile();

    // Sixteen digits' worth of shape for a Visa, and the four the contract actually carries.
    expect(screen.getByText('•••• •••• •••• 4242')).toBeDefined();
    expect(screen.getByText('Caduca 12/27')).toBeDefined();
    // A row of bullets read out loud says nothing; this is what replaces it.
    expect(screen.getByText('Tarjeta Visa terminada en 4242, caduca en 12/27')).toBeDefined();
  });

  it('keeps the card controls folded away until you mean to change it', async () => {
    const user = userEvent.setup();
    await renderProfile();

    expect(screen.queryByLabelText('Cuatro últimos dígitos')).toBeNull();

    await user.click(screen.getByRole('button', { name: 'Cambiar tarjeta' }));

    expect(field('Cuatro últimos dígitos')).toHaveProperty('value', '4242');
    expect(field('Mes de caducidad')).toHaveProperty('value', '12');
    expect(field('Año de caducidad')).toHaveProperty('value', '2027');
    // Opening a block and leaving the reader at the top of the page is half a job.
    expect(document.activeElement).toBe(field('Cuatro últimos dígitos'));
  });

  it('never asks for the card number or the CVV, says so, and does not let the browser offer one', async () => {
    const user = userEvent.setup();
    await renderProfile();

    // Said out loud instead of left to be deduced from a missing field — the whole point of the
    // masked card (`docs/08-identidad-y-seguridad.md` §5).
    expect(
      screen.getByText(
        'No pedimos ni guardamos el número completo ni el CVV: solo los cuatro últimos dígitos y la caducidad.',
      ),
    ).toBeDefined();

    // And with the editor open, where a PAN field would have to live, there is still none.
    await user.click(screen.getByRole('button', { name: 'Cambiar tarjeta' }));
    expect(screen.queryByLabelText(/cvv/i)).toBeNull();
    expect(screen.queryByLabelText(/número de tarjeta/i)).toBeNull();
    // No accessible query can assert the absence of an autofill hint, so this one goes by attribute.
    expect(document.querySelectorAll('[autocomplete="cc-number"], [autocomplete="cc-csc"]')).toHaveLength(0);
  });

  it('shows the email as a value, not as a field that refuses every keystroke', async () => {
    await renderProfile();

    // It is a projection of the token claim; the credential is changed in `ukun-auth` (RF-06).
    expect(screen.getByRole('group', { name: 'Correo' }).textContent).toBe('customer@ukun.dev');
    // Named like a field, but there is no control there to type into.
    expect(screen.queryByRole('textbox', { name: 'Correo' })).toBeNull();
    expect(screen.getByText('El correo y la contraseña se cambian en tu cuenta Ukun, no aquí.')).toBeDefined();
  });

  it('says that editing the profile leaves past orders alone (ADR-0011)', async () => {
    await renderProfile();

    expect(
      screen.getByText(
        'Cambiar la dirección o la tarjeta no toca tus pedidos anteriores: cada pedido guarda su propia copia de a dónde se envió y con qué se pagó.',
      ),
    ).toBeDefined();
  });

  it('resolves the favourite store name from the identifier the profile carries', async () => {
    await renderProfile();

    // The profile only knows `favoriteStoreId: 2`; the name comes from `GET /api/stores` (ADR-0017 §2),
    // which is a second read and lands after the form is already on screen.
    expect(await screen.findByRole('option', { name: 'Ukun Gijón' })).toBeDefined();
    // And when it lands, the control has to be showing that store and not the "Ninguna" it opened on.
    expect(field('Tienda favorita')).toHaveProperty('value', '2');
  });

  it('sends the whole profile on save and reports it', async () => {
    const sent: UpdateProfileRequest[] = [];
    server.use(capturingSave(sent));
    const user = userEvent.setup();
    await renderProfile();

    await user.clear(field('Ciudad'));
    await user.type(field('Ciudad'), 'Gijón');
    await user.click(save());

    expect(await screen.findByText('Cambios guardados')).toBeDefined();
    // A replacement and not a patch: every field travels, the untouched ones included.
    expect(sent).toEqual([
      {
        name: 'Ana',
        lastName: 'García',
        locale: 'es',
        address: { street: 'Calle Uría 1', city: 'Gijón', province: 'Asturias', postalCode: '33003' },
        card: { last4: '4242', brand: 'VISA', expMonth: 12, expYear: 2027 },
        favoriteStoreId: 2,
      },
    ]);
  });

  it('clears the favourite store by sending null, not by leaving the field out', async () => {
    const sent: UpdateProfileRequest[] = [];
    server.use(capturingSave(sent));
    const user = userEvent.setup();
    await renderProfile();

    await user.selectOptions(field('Tienda favorita'), '');
    await user.click(save());

    await screen.findByText('Cambios guardados');
    expect(sent[0].favoriteStoreId).toBeNull();
  });

  it('reports a save with nothing to save instead of faking one', async () => {
    const user = userEvent.setup();
    await renderProfile();

    await user.click(save());

    // The API answers `200` and persists nothing (RF-05); the screen says so rather than
    // congratulating the reader on a change that never happened.
    expect(await screen.findByText('No había nada que cambiar')).toBeDefined();
    expect(screen.queryByText('Cambios guardados')).toBeNull();
  });

  it('refuses to save what the contract would reject, without asking the server', async () => {
    const sent: UpdateProfileRequest[] = [];
    server.use(capturingSave(sent));
    const user = userEvent.setup();
    await renderProfile();

    await user.clear(field('Código postal'));
    await user.type(field('Código postal'), '330');
    await user.click(save());

    expect(await screen.findByText('El código postal son exactamente cinco cifras')).toBeDefined();
    expect(sent).toEqual([]);
    expect(document.activeElement).toBe(field('Código postal'));
  });

  it('paints a server field error under its own field, translated, and focuses it', async () => {
    server.use(
      profileValidationFailed([
        // The dotted path Spring produces for a nested `@Valid` DTO, untouched by the advice.
        { field: 'address.postalCode', message: 'must be five digits' },
      ]),
    );
    const user = userEvent.setup();
    await renderProfile();

    await user.type(field('Nombre'), 'a');
    await user.click(save());

    expect(await screen.findByText('El código postal son exactamente cinco cifras')).toBeDefined();
    // The operator's English never reaches the screen (`docs/12-error-contract.md` §1).
    expect(screen.queryByText('must be five digits')).toBeNull();
    expect(screen.queryByText('Validation failed for the request body')).toBeNull();
    expect(document.activeElement).toBe(field('Código postal'));
  });

  it('unfolds the card block when the server rejects one of its fields', async () => {
    server.use(
      profileValidationFailed([{ field: 'card.last4', message: 'must be four digits' }]),
    );
    const user = userEvent.setup();
    await renderProfile();

    await user.type(field('Nombre'), 'a');
    await user.click(save());

    // The reason the block can be folded at all: an error inside it opens it and takes the caret
    // there. Folded away, this would be a form that refuses to save and never says why.
    expect(
      await screen.findByText('Son los cuatro últimos dígitos de la tarjeta, cuatro cifras'),
    ).toBeDefined();
    expect(document.activeElement).toBe(field('Cuatro últimos dígitos'));
    // And it cannot be folded back over the error.
    expect(screen.queryByRole('button', { name: 'Cambiar tarjeta' })).toBeNull();
  });

  it('discards the changes back to what the server has', async () => {
    const user = userEvent.setup();
    await renderProfile();

    const discard = () => screen.getByRole('button', { name: 'Descartar cambios' });
    // Nothing to discard yet, and a button that does nothing is worse than no button.
    expect(discard()).toHaveProperty('disabled', true);

    await user.clear(field('Ciudad'));
    await user.type(field('Ciudad'), 'Gijón');
    expect(discard()).toHaveProperty('disabled', false);

    await user.click(discard());

    expect(field('Ciudad')).toHaveProperty('value', 'Oviedo');
    expect(discard()).toHaveProperty('disabled', true);
  });

  it('paints the rule only the server knows: a favourite store that no longer exists', async () => {
    server.use(
      profileValidationFailed([
        { field: 'favoriteStoreId', message: 'does not match an existing store' },
      ]),
    );
    const user = userEvent.setup();
    await renderProfile();

    await user.type(field('Nombre'), 'a');
    await user.click(save());

    expect(await screen.findByText('Esa tienda ya no existe. Elige otra')).toBeDefined();
  });

  it('falls back to the form-level message when the field error belongs to no field', async () => {
    server.use(profileValidationFailed([{ field: 'somethingElse', message: 'is invalid' }]));
    const user = userEvent.setup();
    await renderProfile();

    await user.type(field('Nombre'), 'a');
    await user.click(save());

    // An error nobody can paint next to a control is still an error, and it is said out loud.
    expect(await screen.findByText('Revisa los datos: hay algo que no cuadra')).toBeDefined();
  });

  it('shows a failed read as an error with its translated code, never the raw detail', async () => {
    server.use(profileFails('INTERNAL_ERROR', 500));

    renderWithProviders(<ProfilePage />, { route: '/profile' });

    // A 5xx is worth exactly one more attempt (`retryUnlessClientError`), so the error screen is a
    // second late on purpose — hence the longer wait here and not in the 403 below.
    expect(
      await screen.findByText('Algo ha fallado por nuestra parte. Inténtalo de nuevo', {}, { timeout: 3000 }),
    ).toBeDefined();
    expect(screen.queryByText('Fixed English detail nobody should ever read on screen')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Guardar cambios' })).toBeNull();
  });

  it('tells an employee that the screen is not theirs, in their language', async () => {
    // `@PreAuthorize("hasRole('CUSTOMER')")` on the controller: an employee gets a 403 here.
    server.use(profileFails('FORBIDDEN', 403));

    renderWithProviders(<ProfilePage />, { route: '/profile' });

    expect(await screen.findByText('Tu cuenta no tiene acceso a esto')).toBeDefined();
  });

  it('keeps the form usable when the store list cannot be loaded', async () => {
    const sent: UpdateProfileRequest[] = [];
    server.use(
      capturingSave(sent),
      mswHttp.get('/api/stores', () => HttpResponse.json({ code: 'INTERNAL_ERROR' }, { status: 500 })),
    );
    const user = userEvent.setup();
    await renderProfile();

    expect(await screen.findByText('No hemos podido cargar las tiendas. Inténtalo más tarde.')).toBeDefined();

    await user.type(field('Nombre'), 'a');
    await user.click(save());

    await screen.findByText('Cambios guardados');
    // A side read that failed must not quietly wipe the favourite store the profile already had.
    expect(sent[0].favoriteStoreId).toBe(2);
  });

  it('changes the language of the application when the saved locale changes', async () => {
    const sessionReads: string[] = [];
    server.use(signedInCounting(sessionReads));
    const user = userEvent.setup();
    renderWithProviders(
      <>
        <ProfilePage />
        <SessionProbe />
      </>,
      { route: '/profile' },
    );
    await screen.findByRole('button', { name: 'Guardar cambios' });
    await waitFor(() => expect(sessionReads).toHaveLength(1));

    await user.selectOptions(field('Idioma'), 'en');
    await user.click(save());

    // `locale` is the source of truth while there is a session and it travels between devices
    // (ADR-0019): saving it has to change the language the application is speaking.
    expect(await screen.findByRole('button', { name: 'Save changes' })).toBeDefined();
    expect(i18n.resolvedLanguage).toBe('en');
    // And it travels in `GET /api/me` too, so that cache entry is stale from this moment on.
    await waitFor(() => expect(sessionReads).toHaveLength(2));
  });

  it('leaves the session alone when the language did not change', async () => {
    const sessionReads: string[] = [];
    server.use(signedInCounting(sessionReads));
    const user = userEvent.setup();
    renderWithProviders(
      <>
        <ProfilePage />
        <SessionProbe />
      </>,
      { route: '/profile' },
    );
    await screen.findByRole('button', { name: 'Guardar cambios' });
    await waitFor(() => expect(sessionReads).toHaveLength(1));

    await user.type(field('Nombre'), 'a');
    await user.click(save());

    await screen.findByText('Cambios guardados');
    expect(sessionReads).toHaveLength(1);
  });
});
