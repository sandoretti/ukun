import type { ReactNode } from 'react';

import { Icon } from './Icon';

type StockWarningProps = {
  children: ReactNode;
};

/**
 * What a screen says when it is asking for more units than the online store has — once per cart line,
 * once more in its summary for the reader who scrolled past them, and again on the checkout, where
 * the attempt reports availability line by line (ADR-0016).
 *
 * It stays a warning and never an error: `available` reserves nothing, may move before the order is
 * placed, and what actually validates stock is confirming the order (RF-04). It lives in `shared/ui/`
 * because two features paint it and features never import each other.
 *
 * The `aria-live` region is **not** here. Where the announcement belongs differs at each call site —
 * the line wraps a slot that comes and goes, the panel wraps the notice itself — and a component
 * that announces itself would nest two live regions.
 */
export function StockWarning({ children }: StockWarningProps) {
  return (
    <p className="stock-warning small d-flex align-items-start gap-2 mb-0">
      <Icon name="warning" size="sm" />
      {children}
    </p>
  );
}
