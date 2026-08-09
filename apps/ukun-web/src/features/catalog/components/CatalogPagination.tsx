import { useTranslation } from 'react-i18next';
import { Button } from 'react-bootstrap';

type CatalogPaginationProps = {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
};

/**
 * Where the infinite scroll ends. How many results there are is the toolbar's job; here it is only
 * which page you are on, counted from one like the URL — and nothing at all while there is a single
 * page, because there is nowhere to go.
 */
export function CatalogPagination({ page, totalPages, onPageChange }: CatalogPaginationProps) {
  const { t } = useTranslation('catalog');

  if (totalPages <= 1) {
    return null;
  }

  return (
    <nav
      aria-label={t('pagination.label')}
      className="d-flex flex-wrap align-items-center justify-content-center gap-3 mt-4"
    >
      <Button variant="outline-primary" onClick={() => onPageChange(page - 1)} disabled={page <= 1}>
        {t('pagination.previous')}
      </Button>
      <p className="mb-0 text-body-secondary" aria-live="polite">
        {t('pagination.status', { page, totalPages })}
      </p>
      <Button variant="outline-primary" onClick={() => onPageChange(page + 1)} disabled={page >= totalPages}>
        {t('pagination.next')}
      </Button>
    </nav>
  );
}
