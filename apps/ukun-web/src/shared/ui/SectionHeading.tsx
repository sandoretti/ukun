type SectionHeadingProps = {
  /** Already translated by the caller, as in the rest of `shared/ui` — the namespace is the screen's. */
  title: string;
  lead: string;
  /** How much air before the content: the same two rhythms `Section` uses. */
  spacing?: 'regular' | 'spacious';
};

/**
 * The opening of a band: one `<h2>` and the line that says what it is for.
 *
 * The level is fixed at `h2` and the size at `h3` on purpose. There is a single `<h1>` per page —
 * the hero's — and every band hangs one step below it; leaving the level to the caller is how a page
 * ends up with the four `<h1>` the legacy home had.
 */
export function SectionHeading({ title, lead, spacing = 'regular' }: SectionHeadingProps) {
  return (
    <>
      <h2 className="h3 mb-2">{title}</h2>
      <p className={`text-body-secondary ${spacing === 'spacious' ? 'mb-5' : 'mb-4'}`}>{lead}</p>
    </>
  );
}
