import { useTranslation } from 'react-i18next';
import { Button } from 'react-bootstrap';

import { Icon } from '../../../shared/ui/Icon';

/** The schema's floor (`CHECK (quantity > 0)`): below this there is no line, there is a removal. */
const MIN_QUANTITY = 1;

type QuantityStepperProps = {
  quantity: number;
  /** Names the product in the accessible name of each button — «Add one unit of Nordic sofa». */
  productName: string;
  /** The line has a write in flight: the controls stay visible, but they do not stack requests. */
  busy: boolean;
  /** Receives the **total** quantity of the line, which is what the endpoint takes. */
  onChange: (quantity: number) => void;
};

/**
 * Changing the quantity of a line. Two buttons and a figure, and not a number field with a save
 * button, because `PUT /api/cart/items/{productId}` is an idempotent upsert on the total quantity:
 * a step is already the whole request, with no local state to resync against the answer and no
 * client-side validation duplicating the schema's.
 *
 * The lower bound is the schema's and not a preference: at one unit the way out is the remove
 * button, which says what it does, and not a minus sign that silently deletes the line.
 */
export function QuantityStepper({ quantity, productName, busy, onChange }: QuantityStepperProps) {
  const { t } = useTranslation('cart');

  return (
    <div className="d-inline-flex align-items-center gap-2" role="group" aria-label={t('quantity.group', { product: productName })}>
      <Button
        variant="outline-secondary"
        size="sm"
        disabled={busy || quantity <= MIN_QUANTITY}
        aria-label={t('quantity.decrease', { product: productName })}
        onClick={() => onChange(quantity - 1)}
      >
        <Icon name="remove" size="sm" />
      </Button>

      <span className="cart-quantity text-tabular-nums text-center">{quantity}</span>

      <Button
        variant="outline-secondary"
        size="sm"
        disabled={busy}
        aria-label={t('quantity.increase', { product: productName })}
        onClick={() => onChange(quantity + 1)}
      >
        <Icon name="add" size="sm" />
      </Button>
    </div>
  );
}
