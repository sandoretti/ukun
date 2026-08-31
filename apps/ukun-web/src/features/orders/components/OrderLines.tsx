import { useTranslation } from 'react-i18next';
import { Table } from 'react-bootstrap';

import { formatMoney } from '../../../shared/format/money';
import { useLocale } from '../../../shared/hooks/useLocale';
import type { Order } from '../api/orderKeys';

type OrderLinesProps = {
  order: Order;
};

/**
 * The lines of a placed order, with its total.
 *
 * A real table and not a list of rows made of `div`s: this is tabular data —product, unit price,
 * units, amount— with a header per column, and that is what lets a screen reader announce "unit
 * price, 399,99 €" instead of four numbers in a row.
 *
 * `productName` and `unitPrice` are the snapshots frozen at confirmation time (ADR-0011): renaming or
 * repricing a product does not rewrite what is printed here, which is the whole point of defect #19
 * not being reproduced. The amounts are the server's, never multiplied here.
 */
export function OrderLines({ order }: OrderLinesProps) {
  const { t } = useTranslation('orders');
  const locale = useLocale();

  return (
    <section>
      <h2 className="h5 mb-3">{t('lines.title')}</h2>

      <Table responsive className="align-middle mb-0">
        {/* El `<h2>` de arriba está fuera de la tabla, así que no la nombra: quien recorre la página
            por tablas la oía como «tabla» a secas. Es la única del proyecto que no lo tenía; las
            cuatro del back-office ya llevan su `caption` oculto. */}
        <caption className="visually-hidden">{t('lines.title')}</caption>
        <thead>
          <tr>
            <th scope="col">{t('lines.product')}</th>
            <th scope="col" className="text-end">
              {t('lines.unitPrice')}
            </th>
            <th scope="col" className="text-end">
              {t('lines.quantity')}
            </th>
            <th scope="col" className="text-end">
              {t('lines.total')}
            </th>
          </tr>
        </thead>
        <tbody>
          {order.lines.map((line) => (
            <tr key={line.productId}>
              <td>{line.productName}</td>
              <td className="text-end text-tabular-nums">{formatMoney(line.unitPrice, locale)}</td>
              <td className="text-end text-tabular-nums">{line.quantity}</td>
              <td className="text-end text-tabular-nums">{formatMoney(line.totalPrice, locale)}</td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <th scope="row" colSpan={3} className="text-end">
              {t('total')}
            </th>
            <td className="text-end fw-semibold text-tabular-nums">
              {formatMoney(order.total, locale)}
            </td>
          </tr>
        </tfoot>
      </Table>
    </section>
  );
}
