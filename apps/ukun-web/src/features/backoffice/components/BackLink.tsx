import { Link } from 'react-router-dom';

import { Icon } from '../../../shared/ui/Icon';

type BackLinkProps = {
  to: string;
  /** Where it goes, in words: «Volver al catálogo». Already resolved — this knows no keys. */
  label: string;
};

/**
 * The way back out of a form or a sheet, above its heading.
 *
 * It was the same four lines in the four screens that have one —the new product, the new stock line,
 * the new employee and the product sheet— down to the utility classes and the gap. Four copies of a
 * control is four places for it to drift, and the drift had already started elsewhere: the shop's
 * order detail has the same link with `link-secondary` and a wider gap.
 *
 * **That shop one is deliberately left alone.** Covering both would mean picking one of the two looks
 * and changing the other screen, which is a design decision and not a refactor. If the two are ever
 * meant to look alike, this moves to `shared/ui/` then — with two real users and a decision behind
 * it, not on the guess that they might want to.
 *
 * The arrow is decorative: whatever it means is written next to it.
 */
export function BackLink({ to, label }: BackLinkProps) {
  return (
    <Link to={to} className="d-inline-flex align-items-center gap-1 mb-3">
      <Icon name="arrow_back" size="sm" />
      {label}
    </Link>
  );
}
