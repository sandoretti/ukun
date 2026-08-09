import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Col, Offcanvas, Row } from 'react-bootstrap';

import { toProblem } from '../../shared/api/http';
import { useAvailability } from '../../shared/api/availability';
import { useOnlineStore } from '../../shared/api/stores';
import { EmptyState } from '../../shared/ui/EmptyState';
import { ErrorState } from '../../shared/ui/ErrorState';
import { LoadingState } from '../../shared/ui/LoadingState';
import { ActiveFilters } from './components/ActiveFilters';
import { CatalogFilters } from './components/CatalogFilters';
import { CatalogLoadMore } from './components/CatalogLoadMore';
import { CatalogPagination } from './components/CatalogPagination';
import { CatalogToolbar } from './components/CatalogToolbar';
import { ProductCard } from './components/ProductCard';
import { PAGE_SIZE } from './api/productKeys';
import { useCatalogProducts, useMaxProductPrice, useProductTypes } from './api/queries';
import { countActiveFilters, useCatalogParams } from './useCatalogParams';

export default function CatalogPage() {
  const { t } = useTranslation('catalog');
  const { criteria, page, updateFilters, removeFilter, clearFilters, goToPage } = useCatalogParams();
  const [showFilters, setShowFilters] = useState(false);

  const productsQuery = useCatalogProducts(criteria, page);
  const typesQuery = useProductTypes();
  const maxPriceQuery = useMaxProductPrice();
  const onlineStoreQuery = useOnlineStore();

  // Every chunk loaded so far, in one flat list: the grid does not know it was fetched in pieces.
  const products = useMemo(
    () => productsQuery.data?.pages.flatMap((chunk) => chunk.items) ?? [],
    [productsQuery.data],
  );
  const productIds = useMemo(() => products.map((product) => product.id), [products]);

  // The whole result set, and how many pages of `PAGE_SIZE` it takes — never the API's `totalPages`,
  // which counts in chunks and would say three times as many.
  const totalElements = productsQuery.data?.pages[0].totalElements ?? 0;
  const totalPages = Math.ceil(totalElements / PAGE_SIZE);

  // Catalog and availability are two separate reads composed on screen (ADR-0017 §3): the grid is
  // painted as soon as the products arrive, without waiting for the stock answer.
  const availabilityQuery = useAvailability(onlineStoreQuery.data?.id, productIds);
  const availabilityFailed = onlineStoreQuery.isError || availabilityQuery.isError;

  const types = typesQuery.data ?? [];

  return (
    <>
      <h1 className="h3 mb-4">{t('title')}</h1>

      {/* Canalón ancho a partir de `lg`: los filtros y la rejilla son dos zonas distintas y tienen
          que leerse como tales, no como una columna pegada a la otra. */}
      <Row className="g-4 g-lg-5">
        <Col as="aside" lg={3}>
          {/* `responsive="lg"` is what keeps this a single instance of the form: a sidebar from
              `lg` up, a sliding panel below it, with no duplicated ids and no branch per
              breakpoint. */}
          <Offcanvas
            responsive="lg"
            show={showFilters}
            onHide={() => setShowFilters(false)}
            aria-label={t('filters.title')}
            className="filter-sidebar"
          >
            <Offcanvas.Header closeButton closeLabel={t('filters.close')} className="d-lg-none">
              <Offcanvas.Title>{t('filters.title')}</Offcanvas.Title>
            </Offcanvas.Header>
            {/* Sin sangría a partir de `lg`: ahí ya no es un panel, es la primera columna. */}
            <Offcanvas.Body className="d-block px-lg-0">
              <CatalogFilters
                filters={criteria}
                types={types}
                maxPrice={maxPriceQuery.data}
                onChange={updateFilters}
              />
            </Offcanvas.Body>
          </Offcanvas>
          {typesQuery.isError && <ErrorState code={toProblem(typesQuery.error).code} />}
        </Col>

        <Col lg={9}>
          <CatalogToolbar
            totalElements={productsQuery.data ? totalElements : undefined}
            sort={criteria.sort}
            activeFilterCount={countActiveFilters(criteria)}
            availabilityFailed={availabilityFailed}
            onSortChange={(sort) => updateFilters({ sort })}
            onOpenFilters={() => setShowFilters(true)}
          />

          <ActiveFilters filters={criteria} types={types} onRemove={removeFilter} onClear={clearFilters} />

          {/* A chunk that fails is not the screen failing: the error state is for when there is
              nothing to show, and dropping 48 products already read to report the 49th is worse
              than the failure itself — that one is reported where it happened, next to the button. */}
          {productsQuery.isPending ? (
            <LoadingState />
          ) : productsQuery.isError && products.length === 0 ? (
            <ErrorState
              code={toProblem(productsQuery.error).code}
              extensions={toProblem(productsQuery.error).extensions}
            />
          ) : products.length === 0 ? (
            <EmptyState message={t('empty')} />
          ) : (
            <>
              <Row xs={2} md={3} xl={4} className="g-4">
                {products.map((product) => (
                  <Col key={product.id}>
                    <ProductCard product={product} available={availabilityQuery.data?.get(product.id)} />
                  </Col>
                ))}
              </Row>
              {/* Scroll adds chunks up to `PAGE_SIZE`; from there on it is the pagination's turn. */}
              <CatalogLoadMore
                loaded={products.length}
                total={totalElements}
                hasMore={productsQuery.hasNextPage}
                isLoading={productsQuery.isFetchingNextPage}
                failed={productsQuery.isFetchNextPageError}
                onLoadMore={() => void productsQuery.fetchNextPage()}
              />
              <CatalogPagination page={page} totalPages={totalPages} onPageChange={goToPage} />
            </>
          )}
        </Col>
      </Row>
    </>
  );
}
