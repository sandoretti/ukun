import { useTranslation } from 'react-i18next';
import { Nav } from 'react-bootstrap';

/**
 * The one section of the shell whose screen is not built yet: the about page.
 *
 * They are rendered as **disabled buttons, never as links**: a destination that does not exist is not
 * an `<a>`, and pointing one at a blank screen is worse than not offering it at all. It is the same
 * call `SiteFooter` already makes with its stores block.
 *
 * There was a `ComingSoonButton` here too, for the account actions of the bar and of the sign-in
 * card. It left with the signup screen (RF-06): signing in has been a real button since v0.5 and
 * signing up a real link since v0.6, so no action of the shell is without a flow behind it any more.
 *
 * What a sighted user reads from the dimmed styling, a screen reader reads from the accessible name:
 * "News, coming soon". The whole sentence is one translation key with the caption interpolated
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
