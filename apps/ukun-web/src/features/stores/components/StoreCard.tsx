import { useTranslation } from 'react-i18next';
import { Badge, Card } from 'react-bootstrap';

import type { Store } from '../../../shared/api/stores';
import { formatPhone } from '../formatPhone';

type StoreCardProps = {
  store: Store;
};

/**
 * One store, with the address and the phone number RF-02 asks a public listing to carry.
 *
 * The online one is told apart by `isOnline` — never by its name, and with no defensive branch for a
 * second one: exactly one row has it, and that is a database invariant (`ux_store_online`), not a
 * convention. It is marked because it is the only store orders are served from (RF-04), and the note
 * is there so the badge does not read as an invitation to visit a warehouse.
 */
export function StoreCard({ store }: StoreCardProps) {
  const { t } = useTranslation('stores');

  const titleId = `store-${store.id}-title`;
  const { street, postalCode, city, province } = store.address;
  // Built here and not inside the JSX so the postal code and the city stay one line of the address
  // instead of two nodes with a space between them. Spanish five-digit code, no country: it is what
  // the contract's `Address` carries.
  const locality = `${postalCode} ${city}`;

  return (
    // An `article` named by its own heading: the grid reads as a list of named regions rather than a
    // pile of boxes. The border is what carries the distinction for a sighted reader; the badge and
    // the note are what carry it for everyone else.
    <Card
      as="article"
      aria-labelledby={titleId}
      className={`h-100${store.isOnline ? ' border-primary' : ''}`}
    >
      <Card.Body className="d-flex flex-column">
        <div className="d-flex align-items-start justify-content-between gap-2 mb-2">
          <Card.Title as="h2" id={titleId} className="h5 mb-0">
            {store.name}
          </Card.Title>
          {store.isOnline && (
            <Badge bg="primary" className="flex-shrink-0">
              {t('online.badge')}
            </Badge>
          )}
        </div>

        {store.isOnline && <p className="small text-body-secondary mb-3">{t('online.note')}</p>}

        {/* `address` is the element for exactly this, and Bootstrap's reboot already keeps it from
            being italicised. One element per line instead of `<br>`: the break belongs to how the
            address is laid out, not to its content, and each line stays a node of its own.
            `mt-auto` on the phone below is what lines the links up across a row of cards of
            different heights. */}
        <address className="mb-3">
          <span className="d-block">{street}</span>
          <span className="d-block">{locality}</span>
          <span className="d-block">{province}</span>
        </address>

        {/* The number is actionable, which on a phone is the whole point of a store listing. The
            `href` carries the raw E.164 value the dialler understands; the text is the readable
            grouping. The accessible name says which store it calls — a screen reader listing the
            links of this page would otherwise read five indistinguishable numbers. */}
        <a
          className="mt-auto text-tabular-nums"
          href={`tel:${store.phone}`}
          aria-label={t('card.call', { store: store.name })}
        >
          {formatPhone(store.phone)}
        </a>
      </Card.Body>
    </Card>
  );
}
