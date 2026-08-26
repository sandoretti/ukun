import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';

import type { StockView } from '../api/stockKeys';

type StockViewSwitchProps = {
  current: StockView;
  /** Where each view lives, filters carried over. Comes from `useStockParams`. */
  hrefForView: (view: StockView) => string;
};

const VIEWS = [
  { view: 'STOCKED', key: 'stocked' },
  { view: 'NOT_STOCKED', key: 'notStocked' },
  { view: 'ALL', key: 'all' },
] as const satisfies readonly { view: StockView; key: string }[];

/**
 * Which of the three views of RF-09 is on screen: what the store holds, what it could hold, or both.
 *
 * **A segmented control and not a select**, because the three options are mutually exclusive views
 * of the same store and there are only three: a select would hide two of them behind a click, and a
 * checkbox cannot name the alternative it turns off. Everything is visible and one click away.
 *
 * **They stay links.** Which view is on screen is part of the answer the URL gives, like every other
 * filter (`docs/07-frontend-ux-ui.md`), so each one is addressable, opens in a new tab, and the back
 * button walks between them — none of which a radio group would keep. `aria-current` is what says
 * which one you are on; the styling that says it too is only styling.
 */
export function StockViewSwitch({ current, hrefForView }: StockViewSwitchProps) {
  const { t } = useTranslation('backoffice');

  return (
    <div className="btn-group" role="group" aria-label={t('stock.views.label')}>
      {VIEWS.map((option) => (
        <Link
          key={option.view}
          to={hrefForView(option.view)}
          className={`btn btn-sm ${current === option.view ? 'btn-primary' : 'btn-outline-primary'}`}
          aria-current={current === option.view ? 'page' : undefined}
        >
          {t(`stock.views.${option.key}`)}
        </Link>
      ))}
    </div>
  );
}
