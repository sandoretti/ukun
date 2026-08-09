import { useTranslation } from 'react-i18next';
import { Button } from 'react-bootstrap';

type CatalogPaginationProps = {
  page: number;
  totalPages: number;
  totalElements: number;
  onPageChange: (page: number) => void;
};

export function CatalogPagination({ page, totalPages, totalElements, onPageChange }: CatalogPaginationProps) {
  const { t } = useTranslation('catalog');

  return (
    <nav aria-label={t('pagination.label')} className="d-flex flex-wrap align-items-center justify-content-between gap-3 mt-4">
      {/* The count moves under the user's feet as they filter, so it is announced. */}
      <p className="mb-0 text-muted" aria-live="polite">
        {t('pagination.status', { page: page + 1, totalPages, total: totalElements })}
      </p>
      <div className="d-flex gap-2">
        <Button variant="outline-primary" onClick={() => onPageChange(page - 1)} disabled={page <= 0}>
          {t('pagination.previous')}
        </Button>
        <Button variant="outline-primary" onClick={() => onPageChange(page + 1)} disabled={page >= totalPages - 1}>
          {t('pagination.next')}
        </Button>
      </div>
    </nav>
  );
}
