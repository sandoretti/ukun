import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert, Button, Modal } from 'react-bootstrap';

import { toProblem, type ProblemDetailError } from '../../../shared/api/http';
import { Dialog } from '../../../shared/ui/Dialog';
import { ErrorState } from '../../../shared/ui/ErrorState';
import type { AdminProduct } from '../api/adminProductKeys';
import { useDeleteProduct, useDiscontinueProduct } from '../api/adminProductQueries';

type DeleteProductModalProps = {
  product: AdminProduct;
  onClose: () => void;
  /** Where the sheet goes once the product it was showing no longer exists. */
  onDeleted: () => void;
};

/**
 * Deleting a product for good, and the refusal that is the whole point of the dialog.
 *
 * Deletion is physical and only possible for a product that has **never** been referenced (ADR-0013).
 * When it has, the server answers `409 PRODUCT_IN_USE` **saying where** — how many orders, how many
 * stores hold stock of it, how many carts have it — and that is what gets painted, from the `code` and
 * its extensions, never as a generic "cannot do that". Next to it sits the way out the requirement
 * itself names: discontinuing, which is reversible and leaves the history alone.
 *
 * The alternative is only offered while it is one: a product that is already discontinued and still
 * referenced has nothing left to press, and the dialog does not pretend otherwise.
 */
export function DeleteProductModal({ product, onClose, onDeleted }: DeleteProductModalProps) {
  const { t } = useTranslation('backoffice');
  const remove = useDeleteProduct();
  const discontinue = useDiscontinueProduct();

  /** The refusal, kept as the mode it is: it survives whatever is pressed next inside the dialog. */
  const [inUse, setInUse] = useState<ProblemDetailError | null>(null);
  const [failure, setFailure] = useState<unknown>(null);

  const confirm = () => {
    setInUse(null);
    setFailure(null);

    remove.mutate(product.id, {
      onSuccess: onDeleted,
      onError: (error) => {
        const problem = toProblem(error);

        if (problem.code === 'PRODUCT_IN_USE') {
          setInUse(problem);
        } else {
          setFailure(error);
        }
      },
    });
  };

  const isBusy = remove.isPending || discontinue.isPending;

  return (
    <Dialog title={t('products.delete.title')} closeLabel={t('products.delete.close')} onClose={onClose}>
      <Modal.Body>
        {inUse === null ? (
          <>
            <p>{t('products.delete.body', { product: product.name })}</p>
            <Alert variant="secondary" className="mb-0">
              {t('products.delete.notDiscontinue')}
            </Alert>
          </>
        ) : (
          <Alert variant="warning" className="mb-0">
            <h3 className="h6">{t('products.delete.inUse.title')}</h3>
            {/* The sentence of the closed registry, with the problem's own extensions as its
                interpolation parameters: how many orders, stores and carts hold it. */}
            <p>{t(`errors:${inUse.code}`, inUse.extensions)}</p>
            <p className="mb-0">{t('products.delete.inUse.wayOut')}</p>
          </Alert>
        )}

        <div aria-live="polite">
          {failure !== null && <ErrorState error={failure} />}
          {discontinue.isError && <ErrorState error={discontinue.error} />}
        </div>
      </Modal.Body>

      <Modal.Footer>
        <Button variant="light" onClick={onClose} disabled={isBusy}>
          {t('products.delete.cancel')}
        </Button>

        {inUse !== null && product.isActive && (
          <Button
            variant="primary"
            onClick={() => discontinue.mutate(product.id, { onSuccess: onClose })}
            disabled={isBusy}
          >
            {t('products.delete.inUse.discontinue')}
          </Button>
        )}

        {inUse === null && (
          <Button variant="danger" onClick={confirm} disabled={isBusy}>
            {t('products.delete.confirm')}
          </Button>
        )}
      </Modal.Footer>
    </Dialog>
  );
}
