import type { ReactNode } from 'react';

import { Icon } from '../../../shared/ui/Icon';

type StockWarningProps = {
  children: ReactNode;
};

/**
 * What the cart says when it is asking for more units than the online store has — once per line, and
 * once more in the summary for the reader who scrolled past them.
 *
 * It stays a warning and never an error: `available` reserves nothing, may move before the order is
 * placed, and what actually validates stock is confirming the order (RF-04). The two call sites had
 * the same paragraph and the same colour rule written twice; this is that shape in one place.
 *
 * The `aria-live` region is **not** here. Where the announcement belongs differs at each call site —
 * the line wraps a slot that comes and goes, the panel wraps the notice itself — and a component
 * that announces itself would nest two live regions.
 */
export function StockWarning({ children }: StockWarningProps) {
  return (
    <p className="cart-warning small d-flex align-items-start gap-2 mb-0">
      <Icon name="warning" size="sm" />
      {children}
    </p>
  );
}
