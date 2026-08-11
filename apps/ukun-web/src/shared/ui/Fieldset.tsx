import type { ReactNode } from 'react';

type FieldsetProps = {
  /** What the group of fields is about. Rendered as the `<legend>`, never as a loose heading. */
  legend: string;
  /** A line under the legend explaining the group — why a card only keeps four digits, say. */
  description?: string;
  children: ReactNode;
};

/**
 * A group of related fields inside a form.
 *
 * It exists because `docs/07-frontend-ux-ui.md` §Accesibilidad names the defect by hand: the legacy
 * signup is one long form with no grouping at all, and the correction it prescribes is `fieldset` +
 * `legend`. A heading over a stack of inputs looks the same and says nothing — the legend is what a
 * screen reader repeats when the reader tabs into each field of the group.
 */
export function Fieldset({ legend, description, children }: FieldsetProps) {
  return (
    <fieldset className="mb-4">
      <legend className="h5 mb-1">{legend}</legend>
      {description !== undefined && <p className="text-body-secondary small mb-3">{description}</p>}
      {children}
    </fieldset>
  );
}
