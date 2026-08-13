import { useTranslation } from 'react-i18next';
import { Alert, Button, Modal } from 'react-bootstrap';

import { ErrorState } from '../../../shared/ui/ErrorState';
import { useDeleteStockLine } from '../api/queries';
import type { StockedLine } from '../api/stockKeys';

const TITLE_ID = 'delete-stock-line-title';

type DeleteStockLineModalProps = {
  storeId: number;
  storeName: string;
  /** `undefined` if somebody else removed the line first — then there is nothing left to confirm. */
  line: StockedLine | undefined;
  onClose: () => void;
};

/**
 * Removing a line from the store's stock, behind a confirmation that says what it is **not**.
 *
 * RF-09 keeps the two apart and so does this dialog, in those words: deleting removes the row and
 * sends the product back to the list of those not added yet, while setting the units to zero leaves
 * the line in place and the product still managed here. They look alike from the table and mean
 * different things to whoever walks into the shop asking for one.
 */
export function DeleteStockLineModal({ storeId, storeName, line, onClose }: DeleteStockLineModalProps) {
  const { t } = useTranslation('backoffice');
  const remove = useDeleteStockLine(storeId);

  const confirm = () => {
    if (line !== undefined) {
      remove.mutate(line.lineId, { onSuccess: onClose });
    }
  };

  return (
    <Modal show onHide={onClose} centered aria-labelledby={TITLE_ID}>
      <Modal.Header closeButton closeLabel={t('delete.close')}>
        <Modal.Title id={TITLE_ID} className="h5">
          {t('delete.title')}
        </Modal.Title>
      </Modal.Header>

      <Modal.Body>
        {line === undefined ? (
          t('edit.gone')
        ) : (
          <>
            <p>{t('delete.body', { product: line.productName, store: storeName })}</p>
            <Alert variant="secondary" className="mb-0">
              {t('delete.notZero')}
            </Alert>
          </>
        )}

        <div aria-live="polite">{remove.isError && <ErrorState error={remove.error} />}</div>
      </Modal.Body>

      <Modal.Footer>
        <Button variant="light" onClick={onClose} disabled={remove.isPending}>
          {t('delete.cancel')}
        </Button>
        {line !== undefined && (
          <Button variant="danger" onClick={confirm} disabled={remove.isPending}>
            {t('delete.confirm')}
          </Button>
        )}
      </Modal.Footer>
    </Modal>
  );
}
