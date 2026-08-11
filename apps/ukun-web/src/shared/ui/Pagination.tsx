import { useTranslation } from 'react-i18next';
import { Button } from 'react-bootstrap';

type PaginationProps = {
  page: number;
  totalPages: number;
  /**
   * The accessible name of the landmark, already translated by the caller: "catalogue pagination"
   * and "order history pagination" are different landmarks on different screens, and a page with two
   * navigations named alike is a page a screen reader cannot tell apart.
   */
  label: string;
  onPageChange: (page: number) => void;
};

/**
 * Moving between pages of a listing, counted from one like the URL — and nothing at all while there
 * is a single page, because there is nowhere to go.
 *
 * It lives in `shared/ui/` because the catalogue and the order history page the same way, and
 * features never import each other (`docs/07-frontend-ux-ui.md`). Only the landmark's name travels
 * as a prop; the two buttons and the position read the same in every listing, so they come from
 * `common`.
 */
export function Pagination({ page, totalPages, label, onPageChange }: PaginationProps) {
  const { t } = useTranslation('common');

  if (totalPages <= 1) {
    return null;
  }

  return (
    <nav
      aria-label={label}
      className="d-flex flex-wrap align-items-center justify-content-center gap-3 mt-4"
    >
      <Button variant="outline-primary" onClick={() => onPageChange(page - 1)} disabled={page <= 1}>
        {t('pagination.previous')}
      </Button>
      <p className="mb-0 text-body-secondary" aria-live="polite">
        {t('pagination.status', { page, totalPages })}
      </p>
      <Button
        variant="outline-primary"
        onClick={() => onPageChange(page + 1)}
        disabled={page >= totalPages}
      >
        {t('pagination.next')}
      </Button>
    </nav>
  );
}
