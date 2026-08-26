import type { ReactNode } from 'react';
import { Container } from 'react-bootstrap';

type AdminFormBarProps = {
  /** Where the form stands right now — saved, unsaved, saving. Announced, never only coloured. */
  status: string;
  /** The actions. The one that commits goes last, which is where the eye ends up. */
  children: ReactNode;
};

/**
 * The action bar of a long form, anchored to the bottom of the window.
 *
 * A form that does not fit in a viewport cannot keep its save button at the end of the document: the
 * way to it would run past «delete this product», which is the one control nobody should pass through
 * on their way to saving a price. Anchored, the decision is always one press away and always in the
 * same place.
 *
 * The status sits next to the buttons and is `aria-live`, so "saved" is something the screen reader
 * hears and not only something the eye catches.
 */
export function AdminFormBar({ status, children }: AdminFormBarProps) {
  return (
    <div className="admin-form__bar">
      <Container className="d-flex flex-wrap align-items-center justify-content-between gap-3 py-3">
        <p className="overline mb-0" aria-live="polite">
          {status}
        </p>
        <div className="d-flex flex-wrap gap-2">{children}</div>
      </Container>
    </div>
  );
}
