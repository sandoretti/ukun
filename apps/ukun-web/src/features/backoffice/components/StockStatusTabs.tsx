import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Nav } from 'react-bootstrap';

import type { StockStatus } from '../api/stockKeys';

type StockStatusTabsProps = {
  current: StockStatus;
  /** Where the other half lives, filters carried over. Comes from `useStockParams`. */
  hrefForStatus: (status: StockStatus) => string;
};

const TABS = [
  { status: 'STOCKED', key: 'stocked' },
  { status: 'NOT_STOCKED', key: 'notStocked' },
] as const satisfies readonly { status: StockStatus; key: string }[];

/**
 * The two halves of RF-09: the lines this store has, and the catalog products with no row here yet.
 *
 * **They are links, not buttons.** Which half is on screen is part of the answer the URL gives, like
 * every other filter (`docs/07-frontend-ux-ui.md`), so each one is addressable, the back button walks
 * between them, and there is no piece of state to keep in sync with the query. `aria-current` is what
 * says which one you are on — the styling that says it too is only styling.
 */
export function StockStatusTabs({ current, hrefForStatus }: StockStatusTabsProps) {
  const { t } = useTranslation('backoffice');

  return (
    <Nav variant="tabs" as="nav" aria-label={t('stock.tabs.label')} className="mb-4">
      {TABS.map((tab) => (
        <Nav.Link
          key={tab.status}
          as={Link}
          to={hrefForStatus(tab.status)}
          active={current === tab.status}
          aria-current={current === tab.status ? 'page' : undefined}
        >
          {t(`stock.tabs.${tab.key}`)}
        </Nav.Link>
      ))}
    </Nav>
  );
}
