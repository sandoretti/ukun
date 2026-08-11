import { useTranslation } from 'react-i18next';
import { Badge } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import { formatDate } from '../../../shared/format/date';
import { formatMoney } from '../../../shared/format/money';
import { useLocale } from '../../../shared/hooks/useLocale';
import { orderPath } from '../../../shared/routes';
import type { Order } from '../api/orderKeys';

type OrderCardProps = {
  order: Order;
};

/**
 * One order in the history: what it was, when, how much, and the way into it.
 *
 * The heading is the link, so the accessible name of the destination is the order it opens — a list
 * of ten "See order" links reads as ten identical links to a screen reader. The units are counted
 * here because counting is not money arithmetic; every amount is the server's.
 *
 * The status is a value of a closed enum (`OrderStatus`) and is therefore translated, never printed
 * raw: `CONFIRMED` is not a word in either of the two languages this app speaks.
 */
export function OrderCard({ order }: OrderCardProps) {
  const { t } = useTranslation('orders');
  const locale = useLocale();

  const units = order.lines.reduce((total, line) => total + line.quantity, 0);

  return (
    <article className="order-card">
      <div className="d-flex flex-wrap justify-content-between align-items-baseline gap-2">
        <h2 className="h5 mb-0">
          <Link to={orderPath(order.id)} className="order-card__link">
            {t('card.number', { id: order.id })}
          </Link>
        </h2>
        <Badge bg="success-subtle" text="success-emphasis" className="fw-normal">
          {t(`status.${order.status}`)}
        </Badge>
      </div>

      <p className="text-body-secondary small mb-0 mt-1">
        {t('card.placedAt', { date: formatDate(order.placedAt, locale) })} ·{' '}
        {t('card.units', { count: units })}
      </p>

      <p className="fs-5 fw-semibold text-tabular-nums mb-0 mt-2">
        {formatMoney(order.total, locale)}
      </p>
    </article>
  );
}
