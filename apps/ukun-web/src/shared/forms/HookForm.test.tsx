import { useForm } from 'react-hook-form';
import { Form } from 'react-bootstrap';
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { HookForm } from './HookForm';

type Values = { name: string };

/** Written once and read by both the fixture and the query — and out of the JSX, where the
    `i18next/no-literal-string` rule (correctly) does not care that this is only a test. */
const SUBMIT = 'Guardar';

function Subject({ onValid }: { onValid: (values: Values) => void }) {
  const { register, handleSubmit } = useForm<Values>({ defaultValues: { name: '' } });

  return (
    <HookForm className="settings-form" onSubmit={handleSubmit(onValid)}>
      <input aria-label="Nombre" {...register('name')} />
      <button type="submit">{SUBMIT}</button>
    </HookForm>
  );
}

/** There is no ARIA role for a form without an accessible name, so this is the honest way to it. */
const formOf = (container: HTMLElement) => container.querySelector('form') as HTMLFormElement;

describe('HookForm', () => {
  it('hands the values over on submit, like the plain form it replaces', async () => {
    const user = userEvent.setup();
    const onValid = vi.fn();

    render(<Subject onValid={onValid} />);

    await user.type(screen.getByLabelText('Nombre'), 'Estanterías');
    await user.click(screen.getByRole('button', { name: SUBMIT }));

    expect(onValid).toHaveBeenCalledWith({ name: 'Estanterías' }, expect.anything());
  });

  it('cancels the submit event, which is what stops the browser from navigating', () => {
    const { container } = render(<Subject onValid={vi.fn()} />);

    // `fireEvent` answers `false` when the listener called `preventDefault()`. That call is the whole
    // point: without it the browser performs its own submit on top of the mutation, reloading the SPA
    // with every field as a query parameter and wiping the form (issue #151).
    expect(fireEvent.submit(formOf(container))).toBe(false);
  });

  it('turns the browser validation off: the rules are the zod schema’s', () => {
    const { container } = render(<Subject onValid={vi.fn()} />);

    expect(formOf(container).noValidate).toBe(true);
    // And whatever else the caller passes still reaches the element.
    expect(formOf(container).className).toContain('settings-form');
  });

  it('is not cancelled when the event is dropped on the way — the shape this component removes', () => {
    // Not a test of any code that ships: it is what makes the assertion above discriminating. jsdom
    // implements no navigation, so the failure itself is invisible here — the only thing that tells
    // the two wirings apart in a test is this flag.
    function Dropped() {
      const { handleSubmit } = useForm<Values>({ defaultValues: { name: '' } });
      const onSubmit = handleSubmit(vi.fn());

      return <Form noValidate onSubmit={() => void onSubmit()} />;
    }

    const { container } = render(<Dropped />);

    expect(fireEvent.submit(formOf(container))).toBe(true);
  });
});
