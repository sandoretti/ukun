import { useTranslation } from 'react-i18next';

import type { Store } from '../../../shared/api/stores';
import { formatPhone } from '../formatPhone';

type StorePhoneLinkProps = {
  store: Store;
  /** The panel paints its links white over the brand green; the card takes the default. */
  className?: string;
};

/**
 * The phone number of a store, actionable — which on a phone is half the reason a store listing
 * exists. Shared by the grid card and the online panel.
 *
 * The `href` carries the raw E.164 value the dialler understands and the text the readable grouping.
 * The accessible name says which store it calls: without it a screen reader listing the links of
 * this page reads five indistinguishable numbers.
 */
export function StorePhoneLink({ store, className }: StorePhoneLinkProps) {
  const { t } = useTranslation('stores');

  return (
    <a
      className={['text-tabular-nums', className].filter(Boolean).join(' ')}
      href={`tel:${store.phone}`}
      aria-label={t('card.call', { store: store.name })}
    >
      {formatPhone(store.phone)}
    </a>
  );
}
