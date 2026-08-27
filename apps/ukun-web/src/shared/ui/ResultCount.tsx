type ResultCountProps = {
  /**
   * How many rows were found, **already resolved to a sentence** — `shared/ui` knows nothing about
   * translation keys, the same as `EmptyState` and `TextField`. `undefined` while the answer has not
   * arrived.
   */
  label?: string;
};

/**
 * How many rows the listing found, announced politely.
 *
 * The four listings of the back-office and the catalog wrote this line character for character, and
 * the part that matters is the part that would drift: **the paragraph is always mounted**, empty
 * while the count is unknown, instead of appearing with the number in it. A live region that is
 * inserted at the same moment as its content is a live region assistive technology was not watching
 * yet, so the count would simply never be read out. Rendering nothing at all is the mistake this
 * makes impossible to copy.
 *
 * It is a count and not a status: what belongs here is «12 tipos», never «guardado» — that one goes
 * next to what was saved.
 */
export function ResultCount({ label }: ResultCountProps) {
  return (
    <p className="text-body-secondary" aria-live="polite">
      {label ?? null}
    </p>
  );
}
