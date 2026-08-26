import type { ReactNode } from 'react';

import { ActiveFilterChips, type FilterChip } from './ActiveFilterChips';

type FilterToolbarProps = {
  /**
   * What this group of controls narrows down ("staff of the store", "the stock"). Rendered as the
   * accessible name of the region and **not** as a heading: a visible "Filter" over a row of
   * controls that already look like filters is a word that only takes up space. A screen reader
   * still gets it, which is the part that matters.
   */
  label: string;
  /** What can be searched or filtered, said under the bar — where it does not misalign the row. */
  hint?: string;
  /** What is filtered right now, already worded. Empty means nothing is. */
  chips: FilterChip[];
  onRemove: (key: string) => void;
  onClear: () => void;
  /** The controls, in reading order: the search first, then what narrows it down. */
  children: ReactNode;
};

/**
 * The filter bar of a listing: one row, framed, with the search first and the rest of the controls
 * next to it.
 *
 * **One row is the whole design.** Stacking a visible label over each control turns a bar into three
 * rows of different heights, and a hint under one control and not the others is what pushed the
 * neighbouring select out of line. Here every control sits on the same line, each one names itself
 * for assistive technology, and whatever needs explaining goes under the bar, once.
 *
 * Under it go the chips of what is filtered right now, which is how a filter is removed: one at a
 * time by its own ✕, or all at once. A lone "clear filters" link said neither *what* was filtered
 * nor let anybody drop just one of them.
 *
 * It lives in `shared/ui/` because both back-office listings filter this way —the stock (RF-09) and
 * the staff (RF-11)— and features never import each other. What it does not do is decide *which*
 * controls there are, or hold any state: the URL holds the answer (`docs/07-frontend-ux-ui.md`), and
 * each screen passes its own children.
 */
export function FilterToolbar({ label, hint, chips, onRemove, onClear, children }: FilterToolbarProps) {
  return (
    // `<search>` is the element for exactly this. The explicit role is not redundancy for its own
    // sake: the element is from 2023 and screen readers —and jsdom, which is where this is tested—
    // do not all map it to the landmark yet, so the role is what makes it announced today.
    <search role="search" aria-label={label} className="mt-3 mb-4">
      <div className="filter-toolbar">{children}</div>

      {hint !== undefined && <p className="form-text mt-2 mb-0">{hint}</p>}

      {/* Under the bar and not inside it: the controls are what you filter *with*, and the chips are
          what is filtered *now* — two different things, and the row stays one row. */}
      <div className="mt-2">
        <ActiveFilterChips chips={chips} onRemove={onRemove} onClear={onClear} />
      </div>
    </search>
  );
}
