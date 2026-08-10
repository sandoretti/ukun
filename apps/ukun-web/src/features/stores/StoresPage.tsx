import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Col, Container, Row } from 'react-bootstrap';

import { useStores } from '../../shared/api/stores';
import { QueryBoundary } from '../../shared/ui/QueryBoundary';
import { StoreCard } from './components/StoreCard';
import { StoreFilter } from './components/StoreFilter';
import { filterStores, useStoreSearch } from './useStoreSearch';

/**
 * The public listing of stores (RF-02), at `/stores`. Public for real: it is read with no session,
 * and `useStores()` hits an endpoint declared `security: []`.
 *
 * The read is the one shared with the catalogue — same hook, same key, same hour of `staleTime`
 * (`shared/api/stores.ts`). The filter runs on top of what that cache already holds instead of
 * travelling as the contract's `q`: stores are closed reference data, and a request per typed term
 * would trade a cache entry that lasts the session for one that lasts a keystroke.
 *
 * The three mandatory states are `QueryBoundary`'s, not this screen's (`docs/07-frontend-ux-ui.md`).
 */
export default function StoresPage() {
  const { t } = useTranslation('stores');
  const { term, setTerm } = useStoreSearch();
  const storesQuery = useStores();

  const stores = useMemo(() => filterStores(storesQuery.data, term), [storesQuery.data, term]);

  return (
    // El ancho lo pone cada pantalla y no el layout, como en el catálogo.
    <Container className="py-4">
      <h1 className="h3 mb-2">{t('title')}</h1>
      <p className="text-body-secondary mb-4">{t('lead')}</p>

      {/* El campo no ocupa el ancho de la página: un buscador de una línea a sangre completa se lee
          como un formulario, y esto es un filtro sobre un puñado de filas. */}
      <Row>
        <Col md={8} lg={5}>
          <StoreFilter
            value={term}
            onChange={setTerm}
            status={stores === undefined ? '' : t('results', { count: stores.length })}
          />
        </Col>
      </Row>

      {/* Two different ways of having nothing to show, and they do not say the same thing: a filter
          that matched nothing is the reader's doing and is undone by clearing the field; a listing
          with no stores at all is not. */}
      <QueryBoundary
        query={storesQuery}
        data={stores}
        empty={term.trim() === '' ? t('empty.none') : t('empty.filtered', { term: term.trim() })}
      >
        {(items) => (
          <Row xs={1} md={2} xl={3} className="g-4">
            {items.map((store) => (
              <Col key={store.id}>
                <StoreCard store={store} />
              </Col>
            ))}
          </Row>
        )}
      </QueryBoundary>
    </Container>
  );
}
