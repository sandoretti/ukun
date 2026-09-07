import type { ReactNode } from 'react';

import { Section } from './Section';

type ClosingBandProps = {
  /** Already translated by the caller, as in the rest of `shared/ui` — the namespace is the screen's. */
  title: string;
  lead: string;
  /**
   * The way out. `children` and not an `action` prop, for the same reason `NarrativeSection` takes
   * its prose that way: the band owns the layout and the caller owns what goes in it — which is what
   * lets one screen close with a single button and another with two, without this file growing a
   * `secondaryAction` that only one of them ever fills.
   */
  children: ReactNode;
  /** A line under the actions, for a screen that has something to add after the call. Optional. */
  note?: string;
};

/**
 * The last band of a long page: the brand-green closing with its call to action.
 *
 * It is in `shared/ui/` and not in the screen that first had it because two screens close this way
 * now — the landing page and «Sobre Ukun» — and the second one was written as a near-copy of the
 * first before this component existed. That is the whole reason it exists: same `<h2>`, same capped
 * lead, same tone and spacing, and the actions centred under them. Two copies of that would have
 * drifted the first time one of the two got a different button size.
 *
 * The heading is fixed at `h2` and the tone at `brand`, like `SectionHeading` fixes its own level:
 * a page has one `<h1>` and this band is never it, and a closing that is not the brand colour is a
 * different band, not this one with a flag flipped.
 */
export function ClosingBand({ title, lead, children, note }: ClosingBandProps) {
  return (
    <Section tone="brand" spacing="spacious" className="closing-cta" containerClassName="text-center">
      <h2 className="h2 mb-3">{title}</h2>
      <p className="lead closing-cta__lead mx-auto mb-4">{lead}</p>
      {/* Envuelve siempre, aunque venga una sola acción: con `flex-wrap` y `gap`, dos botones caben
          en móvil sin que el segundo se pegue al primero, y con uno no cambia nada. */}
      <div className="d-flex flex-wrap justify-content-center gap-3">{children}</div>
      {note !== undefined && <p className="closing-cta__note mb-0 mt-4 small">{note}</p>}
    </Section>
  );
}
