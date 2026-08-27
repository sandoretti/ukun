import { Modal } from 'react-bootstrap';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { Dialog } from './Dialog';

const TITLE = 'Borrar tipo de producto';
const CLOSE = 'Cerrar';

function open(title = TITLE, onClose = vi.fn()) {
  render(
    <Dialog title={title} closeLabel={CLOSE} onClose={onClose}>
      <Modal.Body>{title}</Modal.Body>
    </Dialog>,
  );

  return onClose;
}

describe('Dialog', () => {
  it('is named by its own title, which is what a screen reader announces on opening', () => {
    open();

    // `getByRole('dialog', { name })` only finds it if `aria-labelledby` really resolves to the title.
    expect(screen.getByRole('dialog', { name: TITLE })).toBeDefined();
  });

  it('gives the cross a name of its own, apart from anything in the footer', async () => {
    const user = userEvent.setup();
    const onClose = open();

    await user.click(screen.getByRole('button', { name: CLOSE }));

    expect(onClose).toHaveBeenCalledOnce();
  });

  it('generates the title id, so two dialogs never point at the same node', () => {
    render(
      <>
        <Dialog title="Uno" closeLabel={CLOSE} onClose={vi.fn()}>
          <Modal.Body />
        </Dialog>
        <Dialog title="Dos" closeLabel={CLOSE} onClose={vi.fn()}>
          <Modal.Body />
        </Dialog>
      </>,
    );

    // The literal `TITLE_ID` constants this replaces were unique only by inspection: two dialogs
    // mounted at once shared it, and both `aria-labelledby` resolved to the first title.
    const [first, second] = screen.getAllByRole('dialog');
    const labelOf = (dialog: HTMLElement) => dialog.getAttribute('aria-labelledby');

    expect(labelOf(first)).not.toBe(labelOf(second));
    expect(screen.getByRole('dialog', { name: 'Dos' })).toBe(second);
  });
});
