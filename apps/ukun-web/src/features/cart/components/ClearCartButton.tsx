import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Modal } from 'react-bootstrap';

import { Icon } from '../../../shared/ui/Icon';

type ClearCartButtonProps = {
  busy: boolean;
  onConfirm: () => void;
};

/**
 * Emptying the cart, behind a confirmation.
 *
 * It is the only action on this screen that cannot be undone with one click: removing a line is a
 * trip back to the product sheet, and a quantity is a step away. Wiping every line is neither, so it
 * asks — the modal traps focus and names itself from its own title, which is what makes the question
 * reach a screen reader too.
 */
export function ClearCartButton({ busy, onConfirm }: ClearCartButtonProps) {
  const { t } = useTranslation('cart');
  const [asking, setAsking] = useState(false);

  const confirm = () => {
    setAsking(false);
    onConfirm();
  };

  return (
    <>
      <Button
        variant="outline-danger"
        className="d-inline-flex align-items-center gap-1"
        disabled={busy}
        onClick={() => setAsking(true)}
      >
        <Icon name="delete" size="sm" />
        {t('clear.action')}
      </Button>

      <Modal show={asking} onHide={() => setAsking(false)} centered aria-labelledby="clear-cart-title">
        {/* The cross closes, it does not cancel: two controls with the same accessible name in one
            dialog are two things a screen reader cannot tell apart. */}
        <Modal.Header closeButton closeLabel={t('clear.close')}>
          <Modal.Title id="clear-cart-title" className="h5">
            {t('clear.title')}
          </Modal.Title>
        </Modal.Header>
        <Modal.Body>{t('clear.body')}</Modal.Body>
        <Modal.Footer>
          <Button variant="light" onClick={() => setAsking(false)}>
            {t('clear.cancel')}
          </Button>
          <Button variant="danger" onClick={confirm}>
            {t('clear.confirm')}
          </Button>
        </Modal.Footer>
      </Modal>
    </>
  );
}
