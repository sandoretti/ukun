import { useTranslation } from 'react-i18next';
import { Button, Spinner } from 'react-bootstrap';

import { useOnScreen } from '../../../shared/hooks/useOnScreen';

type CatalogLoadMoreProps = {
  /** Products already on screen, and how many there are in total for the current filters. */
  loaded: number;
  total: number;
  hasMore: boolean;
  isLoading: boolean;
  /** The last chunk did not arrive. What is already on screen stays; the button is the retry. */
  failed: boolean;
  onLoadMore: () => void;
};

/**
 * The bottom of the grid: how much of the result set is on screen, and the way to add the next
 * chunk. Scrolling into the sentinel loads it on its own, and the button does the same thing for
 * whoever is on a keyboard or never reaches the sentinel — the automatic path is the convenience,
 * not the only one.
 *
 * The progress line stays after the last chunk of the page instead of unmounting with the button:
 * losing the focused element mid-click leaves the reader at the top of the document, and the count
 * is what explains why the grid stopped growing and the pagination showed up.
 */
export function CatalogLoadMore({ loaded, total, hasMore, isLoading, failed, onLoadMore }: CatalogLoadMoreProps) {
  const { t } = useTranslation('catalog');
  // A sentinel that keeps firing after a failure would retry forever; from there on it is a choice.
  const sentinel = useOnScreen(onLoadMore, hasMore && !isLoading && !failed);

  return (
    <div className="d-flex flex-column align-items-center gap-2 mt-5">
      <p className="mb-0 text-body-secondary" aria-live="polite">
        {t('loadMore.progress', { loaded, total })}
      </p>
      {failed && (
        <p className="mb-0 text-body-secondary small" role="status">
          {t('loadMore.failed')}
        </p>
      )}
      {hasMore && (
        <Button type="button" variant="outline-primary" onClick={onLoadMore} disabled={isLoading}>
          {isLoading && <Spinner as="span" animation="border" size="sm" className="me-2" aria-hidden="true" />}
          {t('loadMore.action')}
        </Button>
      )}
      <div ref={sentinel} aria-hidden="true" />
    </div>
  );
}
