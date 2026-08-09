import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert, Col, Row } from 'react-bootstrap';

import { toProblem } from '../../shared/api/http';
import { useAvailability } from '../../shared/api/availability';
import { useOnlineStore } from '../../shared/api/stores';
import { EmptyState } from '../../shared/ui/EmptyState';
import { ErrorState } from '../../shared/ui/ErrorState';
import { LoadingState } from '../../shared/ui/LoadingState';
import { CatalogFilters } from './components/CatalogFilters';
import { CatalogPagination } from './components/CatalogPagination';
import { ProductCard } from './components/ProductCard';
import { useProducts, useProductTypes } from './api/queries';
import { useCatalogParams } from './useCatalogParams';

export default function CatalogPage() {
  const { t } = useTranslation('catalog');
  const { filters, applyFilters, clearFilters, goToPage } = useCatalogParams();

  const productsQuery = useProducts(filters);
  const typesQuery = useProductTypes();
  const onlineStoreQuery = useOnlineStore();

  const products = useMemo(() => productsQuery.data?.items ?? [], [productsQuery.data]);
  const productIds = useMemo(() => products.map((product) => product.id), [products]);

  // Catalog and availability are two separate reads composed on screen (ADR-0017 §3): the grid is
  // painted as soon as the products arrive, without waiting for the stock answer.
  const availabilityQuery = useAvailability(onlineStoreQuery.data?.id, productIds);
  const availabilityFailed = onlineStoreQuery.isError || availabilityQuery.isError;

  return (
    <>
      <h2 className="mb-4">{t('title')}</h2>

      <CatalogFilters
        filters={filters}
        types={typesQuery.data ?? []}
        onApply={applyFilters}
        onClear={clearFilters}
      />

      {typesQuery.isError && <ErrorState code={toProblem(typesQuery.error).code} />}

      {productsQuery.isPending ? (
        <LoadingState />
      ) : productsQuery.isError ? (
        <ErrorState
          code={toProblem(productsQuery.error).code}
          extensions={toProblem(productsQuery.error).extensions}
        />
      ) : products.length === 0 ? (
        <EmptyState message={t('empty')} />
      ) : (
        <>
          {availabilityFailed && (
            // Availability is secondary information: losing it hides the badges, it does not hide
            // the catalog. What it never does is degrade to "unknown" (RF-01).
            <Alert variant="warning">{t('availability.unknown')}</Alert>
          )}
          <Row xs={1} sm={2} lg={3} xl={4} className="g-4">
            {products.map((product) => (
              <Col key={product.id}>
                <ProductCard product={product} available={availabilityQuery.data?.get(product.id)} />
              </Col>
            ))}
          </Row>
          <CatalogPagination
            page={productsQuery.data.page}
            totalPages={productsQuery.data.totalPages}
            totalElements={productsQuery.data.totalElements}
            onPageChange={goToPage}
          />
        </>
      )}
    </>
  );
}
