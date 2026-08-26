import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Badge, Button } from 'react-bootstrap';

import { Icon } from '../../../shared/ui/Icon';
import { isStocked, type StockLineOrCandidate, type StockedLine } from '../api/stockKeys';
import { newStockLinePath } from '../routes';

type StockRowProps = {
  line: StockLineOrCandidate;
  onEdit: (line: StockedLine) => void;
  onDelete: (line: StockedLine) => void;
};

/**
 * One row of the store, whichever of RF-09's two halves it comes from.
 *
 * **The three states it tells apart are three different things**, and the whole reason the crossed
 * view is worth having is that they can be read side by side:
 *
 * - a quantity — the store holds that many;
 * - **zero units** — the line exists, the product is managed here, and there are none left;
 * - **not added yet** — there is no line at all, so the product is not managed in this store. It is
 *   not "zero" (RF-09), and the only thing to do with it is add it, by SKU (ADR-0015).
 *
 * The actions follow from that: a line that exists can be edited or removed, and a product without
 * one can only be added. Nothing is disabled — what does not apply is not drawn.
 */
export function StockRow({ line, onEdit, onDelete }: StockRowProps) {
  const { t } = useTranslation('backoffice');

  if (!isStocked(line)) {
    return (
      <tr>
        <td className="text-tabular-nums">{line.sku}</td>
        <td>{line.productName}</td>
        <td className="text-end">
          <Badge bg="secondary-subtle" className="text-body-secondary">
            {t('stock.row.notStocked')}
          </Badge>
        </td>
        <td>
          <div className="d-flex gap-2 justify-content-end">
            <Link
              to={newStockLinePath(line.sku)}
              className="btn btn-outline-primary btn-sm"
              // Short in the cell and specific in the name: twenty rows with twenty controls all
              // called the same are twenty a screen reader cannot tell apart.
              aria-label={t('stock.row.add', { product: line.productName })}
            >
              {t('stock.row.addAction')}
            </Link>
          </div>
        </td>
      </tr>
    );
  }

  return (
    <tr>
      <td className="text-tabular-nums">{line.sku}</td>
      <td>{line.productName}</td>
      <td className="text-tabular-nums text-end">
        {line.quantity === 0 ? (
          // Zero is not an absence: the product is still managed here, and that is what the badge
          // says out loud so it is not read as "not added yet" two rows below.
          <Badge bg="warning-subtle" className="text-body">
            {t('stock.row.outOfStock')}
          </Badge>
        ) : (
          line.quantity
        )}
      </td>
      <td>
        <div className="d-flex gap-2 justify-content-end">
          <Button
            variant="outline-primary"
            size="sm"
            aria-label={t('stock.row.edit', { product: line.productName })}
            onClick={() => onEdit(line)}
          >
            <Icon name="edit" size="sm" />
          </Button>
          <Button
            variant="outline-danger"
            size="sm"
            aria-label={t('stock.row.delete', { product: line.productName })}
            onClick={() => onDelete(line)}
          >
            <Icon name="delete" size="sm" />
          </Button>
        </div>
      </td>
    </tr>
  );
}
