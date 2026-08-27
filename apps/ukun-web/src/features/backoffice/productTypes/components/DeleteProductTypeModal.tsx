import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert, Button, Modal } from 'react-bootstrap';

import { toProblem, type ProblemDetailError } from '../../../../shared/api/http';
import { Dialog } from '../../../../shared/ui/Dialog';
import { ErrorState } from '../../../../shared/ui/ErrorState';
import type { AdminProductType } from '../api/productTypeKeys';
import { useDeleteProductType } from '../api/queries';

type DeleteProductTypeModalProps = {
  productType: AdminProductType;
  onClose: () => void;
};

/**
 * Deleting a type for good, and the refusal that is most of what this dialog does.
 *
 * A type is either deletable or it is not, and the listing **already knows which**: `productCount`
 * travels with every row precisely so the answer can be given before the request — the contract says
 * as much next to the field («so the UI can say so before trying»). So a type with products opens
 * straight into the refusal, with no destructive button to press: offering one that is guaranteed to
 * fail is asking the reader to discover a rule the screen was already holding.
 *
 * There is **no way out offered next to it**, unlike `DeleteProductModal`, which can discontinue
 * instead. A type has no logical deletion (ADR-0013): what unblocks this is reassigning those
 * products to another type, which happens on the products screen and is said in words here.
 *
 * The `409` is still handled, because `productCount` is a reading and not a lock: a product could
 * have been given this type between the listing and the button. When it arrives, what gets painted is
 * the registry's own sentence with the problem's `inProducts` as its interpolation parameter — the
 * count the server just measured, not the one the row was showing.
 */
export function DeleteProductTypeModal({ productType, onClose }: DeleteProductTypeModalProps) {
  const { t } = useTranslation('backoffice');
  const remove = useDeleteProductType();

  /** The server's refusal, kept as the mode it is rather than as the last error. */
  const [inUse, setInUse] = useState<ProblemDetailError | null>(null);
  const [failure, setFailure] = useState<unknown>(null);

  /** What the row already knew, or what the server just said. Either way, it cannot be deleted. */
  const blocked = productType.productCount > 0 || inUse !== null;
  const inProducts = inUse === null ? productType.productCount : inUse.extensions.inProducts;

  const confirm = () => {
    setFailure(null);

    remove.mutate(productType.id, {
      onSuccess: onClose,
      onError: (error) => {
        const problem = toProblem(error);

        if (problem.code === 'PRODUCT_TYPE_IN_USE') {
          setInUse(problem);
        } else {
          setFailure(error);
        }
      },
    });
  };

  return (
    <Dialog
      title={t('productTypes.delete.title')}
      closeLabel={t('productTypes.delete.close')}
      onClose={onClose}
    >
      <Modal.Body>
        {blocked ? (
          <Alert variant="warning" className="mb-0">
            <h3 className="h6">{t('productTypes.delete.inUse.title')}</h3>
            {/* The sentence of the closed registry, interpolated with the count — the server's when
                it answered, the row's when it never had to be asked. */}
            <p>{t('errors:PRODUCT_TYPE_IN_USE', { inProducts })}</p>
            <p className="mb-0">{t('productTypes.delete.inUse.wayOut')}</p>
          </Alert>
        ) : (
          <>
            <p>{t('productTypes.delete.body', { productType: productType.name })}</p>
            <Alert variant="secondary" className="mb-0">
              {t('productTypes.delete.noProducts')}
            </Alert>
          </>
        )}

        <div aria-live="polite">{failure !== null && <ErrorState error={failure} />}</div>
      </Modal.Body>

      <Modal.Footer>
        <Button variant="light" onClick={onClose} disabled={remove.isPending}>
          {blocked ? t('productTypes.delete.understood') : t('productTypes.delete.cancel')}
        </Button>

        {!blocked && (
          <Button variant="danger" onClick={confirm} disabled={remove.isPending}>
            {t('productTypes.delete.confirm')}
          </Button>
        )}
      </Modal.Footer>
    </Dialog>
  );
}
