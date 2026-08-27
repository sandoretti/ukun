import { useId, type ReactNode } from 'react';
import { Modal } from 'react-bootstrap';

type DialogProps = {
  /** The dialog's heading, and the name a screen reader announces when it opens. */
  title: string;
  /**
   * The accessible name of the closing cross.
   *
   * It is asked for separately, and never defaulted, because it must not read like any button in the
   * footer: two controls with the same accessible name in one dialog are two things a screen reader
   * cannot tell apart. A footer that closes says what closing means there —«Cancelar», «Entendido»—
   * while this one is just «Cerrar».
   */
  closeLabel: string;
  onClose: () => void;
  /** `Modal.Body` and `Modal.Footer`, or the `HookForm` that wraps them both. */
  children: ReactNode;
};

/**
 * The dialog of this app: shown, centered, and named by its own title.
 *
 * The five dialogs of the back-office —borrar un producto, una línea de stock o un tipo, editar
 * unidades, crear o renombrar un tipo— wrote these six lines each, and each carried its own
 * module-level `const TITLE_ID = '…'` to wire `aria-labelledby`. Those constants are what this
 * really removes: a literal id is unique only as long as somebody keeps checking that it is, and two
 * dialogs open at once —or one rendered twice— would silently point two `aria-labelledby` at the same
 * node. `useId` is the answer React already gives, and it is the same one `useFieldAria` uses for
 * labels and errors.
 *
 * The header ends here and `children` starts at the body on purpose: a dialog whose body and footer
 * are a form needs the `<form>` to wrap both —so the footer's `submit` belongs to it, Enter included—
 * and that is the caller's shape to decide, not this component's.
 */
export function Dialog({ title, closeLabel, onClose, children }: DialogProps) {
  const titleId = useId();

  return (
    <Modal show onHide={onClose} centered aria-labelledby={titleId}>
      <Modal.Header closeButton closeLabel={closeLabel}>
        <Modal.Title id={titleId} className="h5">
          {title}
        </Modal.Title>
      </Modal.Header>

      {children}
    </Modal>
  );
}
