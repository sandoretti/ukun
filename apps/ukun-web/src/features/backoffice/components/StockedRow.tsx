import { useTranslation } from 'react-i18next';
import { Button } from 'react-bootstrap';

import { Icon } from '../../../shared/ui/Icon';
import type { StockedLine } from '../api/stockKeys';

type StockedRowProps = {
  line: StockedLine;
  onEdit: (line: StockedLine) => void;
  onDelete: (line: StockedLine) => void;
};

/**
 * One line of stock, with its two actions.
 *
 * They are icon buttons, and each one is named after the product it acts on: a table of twenty rows
 * with twenty buttons called "edit" is a table nobody can navigate by name. The quantity is right
 * aligned and in tabular figures so a column of numbers can be read as a column.
 */
export function StockedRow({ line, onEdit, onDelete }: StockedRowProps) {
  const { t } = useTranslation('backoffice');

  return (
    <tr>
      <td className="text-tabular-nums">{line.sku}</td>
      <td>{line.productName}</td>
      <td className="text-tabular-nums text-end">{line.quantity}</td>
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
