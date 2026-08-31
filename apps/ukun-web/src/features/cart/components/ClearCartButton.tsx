import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Modal } from 'react-bootstrap';

import { Dialog } from '../../../shared/ui/Dialog';
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

      {/* Por `Dialog` como los otros cinco, y no por un `Modal` propio: era el único que se lo
          montaba aparte, y con él volvía el `id` literal (`"clear-cart-title"`) que `Dialog`
          documenta como la razón de existir — único solo mientras alguien siga comprobando que lo
          es. El `useId` de dentro lo resuelve, y de paso este diálogo hereda el título como
          encabezado de verdad. */}
      {asking && (
        <Dialog
          title={t('clear.title')}
          // La cruz cierra, no cancela: dos controles con el mismo nombre accesible en un mismo
          // diálogo son dos cosas que un lector de pantalla no puede distinguir.
          closeLabel={t('clear.close')}
          onClose={() => setAsking(false)}
        >
          <Modal.Body>{t('clear.body')}</Modal.Body>
          <Modal.Footer>
            <Button variant="light" onClick={() => setAsking(false)}>
              {t('clear.cancel')}
            </Button>
            <Button variant="danger" onClick={confirm}>
              {t('clear.confirm')}
            </Button>
          </Modal.Footer>
        </Dialog>
      )}
    </>
  );
}
