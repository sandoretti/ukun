import { useTranslation } from 'react-i18next';
import { Button, Nav } from 'react-bootstrap';

/**
 * The sections of the shell whose screen is not built yet — stores (issue #20), news, the about page,
 * and everything behind an account (auth is v0.4 in `docs/04-roadmap.md`).
 *
 * They are rendered as **disabled buttons, never as links**: a destination that does not exist is not
 * an `<a>`, and pointing one at a blank screen is worse than not offering it at all. It is the same
 * call `SiteFooter` already makes with its stores block, made reusable now that the navigation bar
 * needs it half a dozen times.
 *
 * What a sighted user reads from the dimmed styling, a screen reader reads from the accessible name:
 * "Stores, coming soon". The whole sentence is one translation key with the caption interpolated
 * into it — glueing `label + ' ' + suffix` in code would be assuming that every language puts the
 * qualifier after the noun and separates it with a space. `disabled` also keeps these out of the tab
 * order, so the keyboard walks from the catalogue straight to the search field.
 */

type ComingSoonProps = {
  /** Visible caption, already translated by the caller — these live in the `common` namespace. */
  label: string;
};

/** An entry of the navigation bar whose route does not exist yet. */
export function ComingSoonNavItem({ label }: ComingSoonProps) {
  const { t } = useTranslation('common');

  return (
    <Nav.Link as="button" type="button" disabled aria-label={t('nav.comingSoon', { label })}>
      {label}
    </Nav.Link>
  );
}

type ComingSoonButtonProps = ComingSoonProps & {
  /**
   * Named variant, as in the rest of `shared/ui/` — never a stack of booleans. The two light ones
   * are for the green of the bar; `outline-primary` is for the white of a card.
   */
  variant: 'light' | 'outline-light' | 'outline-primary';
};

/** An action of the shell — signing in, signing up — with no flow behind it yet. */
export function ComingSoonButton({ label, variant }: ComingSoonButtonProps) {
  const { t } = useTranslation('common');

  return (
    <Button
      type="button"
      variant={variant}
      size="sm"
      disabled
      aria-label={t('nav.comingSoon', { label })}
    >
      {label}
    </Button>
  );
}
