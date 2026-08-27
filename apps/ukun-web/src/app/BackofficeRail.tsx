import { useTranslation } from 'react-i18next';
import { NavLink } from 'react-router-dom';
import { OverlayTrigger, Tooltip } from 'react-bootstrap';

import { SignOutForm } from '../features/auth/components/SignOutForm';
import { BACKOFFICE_SECTIONS, type BackofficeSection } from '../features/backoffice/sections';
import { useAuth } from '../shared/hooks/useAuth';
import { BACKOFFICE_PATH } from '../shared/routes';
import { Icon } from '../shared/ui/Icon';

/** A rail entry needs no card body: what it draws is one icon and one caption. */
type RailItem = Omit<BackofficeSection, 'bodyKey'> & {
  /** Only the index needs it: without `end` it stays lit on every screen hanging below it. */
  end?: true;
};

/**
 * The rail's entries: the index, and then whatever `BACKOFFICE_SECTIONS` says the back-office
 * contains.
 *
 * **The list is not copied here on purpose.** The index at `/backoffice` draws the same destinations
 * as cards, and two copies of «what the back-office has» is exactly how the old `/admin` index ended
 * up knowing about the catalogue and the staff but not about the stock.
 *
 * The index is the one entry that is not a section — it is where the sections are listed, so it has
 * no card of its own.
 */
const RAIL_ITEMS: readonly RailItem[] = [
  {
    key: 'home',
    to: BACKOFFICE_PATH,
    icon: 'space_dashboard',
    role: 'EMPLOYEE',
    titleKey: 'rail.home',
    end: true,
  },
  ...BACKOFFICE_SECTIONS,
];

type RailLinkProps = {
  to: string;
  icon: string;
  label: string;
  expanded: boolean;
  end?: boolean;
};

/**
 * One destination of the rail.
 *
 * **The caption is always in the DOM**, only taken off screen while the rail is narrow. That is what
 * makes the mini rail readable by a screen reader without a single `aria-label`: the link is named by
 * its own text either way, and the tooltip is left as what it actually is — a way of showing sighted
 * readers the same word the markup already carries.
 */
function RailLink({ to, icon, label, expanded, end }: RailLinkProps) {
  const link = (
    // `NavLink` sets `aria-current="page"` on the active one, so which screen you are on is said and
    // not only coloured.
    <NavLink to={to} end={end} className="bo-rail__item">
      <Icon name={icon} />
      <span className={expanded ? 'bo-rail__caption' : 'visually-hidden'}>{label}</span>
    </NavLink>
  );

  if (expanded) {
    return link;
  }

  return (
    <OverlayTrigger placement="right" overlay={<Tooltip id={`bo-rail-tip-${to}`}>{label}</Tooltip>}>
      {link}
    </OverlayTrigger>
  );
}

type BackofficeRailProps = {
  expanded: boolean;
  onToggle: () => void;
};

/**
 * The navigation of the back-office: a rail down the left side, icons only until it is widened.
 *
 * **What it offers is decided from the session's roles**, through `useAuth().hasRole` — the one place
 * in the app that reads them. Hiding an entry is UX and never the barrier: the gateway refuses
 * `/api/admin/**` without `ADMIN`, `ukun-api` checks it again, and `RequireRole` turns the route away
 * even if somebody types it (`docs/08-identidad-y-seguridad.md` §4). An employee who is not an
 * administrator simply has a shorter rail.
 *
 * It lives in `app/` rather than in `shared/ui/` because it links into `features/backoffice/`, and a
 * shell component cannot reach down into a feature — this layer is the one that already composes
 * them, as `App.tsx` does with the very same route modules.
 *
 * The way back to the shop is the last thing in it, under a rule. The back-office is deliberately not
 * the shop's shell —no catalogue menu, no product search, no cart— so leaving has to be somewhere,
 * and the alternative was the browser's back button.
 */
export function BackofficeRail({ expanded, onToggle }: BackofficeRailProps) {
  const { t } = useTranslation(['backoffice', 'common']);
  const { hasRole } = useAuth();

  const items = RAIL_ITEMS.filter((item) => hasRole(item.role));

  return (
    <div className={`bo-rail${expanded ? ' bo-rail--expanded' : ''}`}>
      <div className="bo-rail__head">
        {/* The brand as type and not as the logotype: the wordmark is a wide bitmap that does not fit
            a four-rem rail, and `docs/07-frontend-ux-ui.md` already sets the precedent of drawing the
            name in the house typeface where the image would not work. */}
        {expanded && <span className="bo-rail__brand">{t('common:title')}</span>}

        {/* Neither `aria-expanded` nor `aria-pressed`: nothing is disclosed and nothing is hidden —
            the captions are in the markup at both widths. What the button does changes, so its name
            changes with it, which is the honest thing to announce. */}
        <button
          type="button"
          className="bo-rail__toggle"
          aria-label={t(expanded ? 'rail.collapse' : 'rail.expand')}
          onClick={onToggle}
        >
          <Icon name={expanded ? 'left_panel_close' : 'left_panel_open'} />
        </button>
      </div>

      <nav aria-label={t('rail.label')} className="bo-rail__nav">
        <ul className="bo-rail__group">
          {items.map((item) => (
            <li key={item.key}>
              <RailLink
                to={item.to}
                icon={item.icon}
                label={t(item.titleKey)}
                expanded={expanded}
                end={item.end}
              />
            </li>
          ))}
        </ul>

        <ul className="bo-rail__group bo-rail__group--foot">
          <li>
            {/* Its own caption and not `account.backHome`: that one belongs to the «this page is not
                for your account» screen, which can be in the content while this is in the rail — two
                links to the same place with the same name on the same page. */}
            <RailLink to="/" icon="storefront" label={t('rail.shop')} expanded={expanded} />
          </li>
          <li className="bo-rail__signout">
            {/* Signing out is a whole `<form>` posting to the gateway, so it is not a `RailLink` and
                cannot be: the destination is not a route. It wears the rail's dark ground and drops
                its caption with everything else when the rail is narrow. */}
            <SignOutForm
              variant="outline-light"
              labelVisibility={expanded ? 'visible' : 'hidden'}
            />
          </li>
        </ul>
      </nav>
    </div>
  );
}
