import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Badge, Button, Container } from 'react-bootstrap';

import { Icon } from '../../shared/ui/Icon';
import { ResourceBoundary } from '../../shared/ui/ResourceBoundary';
import { ADMIN_PRODUCTS_PATH } from './adminRoutes';
import { useAdminProduct } from './api/adminProductQueries';
import { CatalogStatusPanel } from './components/CatalogStatusPanel';
import { DeleteProductModal } from './components/DeleteProductModal';
import { ProductEditForm } from './components/ProductEditForm';
import { ProductImagePanel } from './components/ProductImagePanel';

/**
 * One product of the back-office catalog (RF-10) at `/admin/products/:id`, and everything that can be
 * decided about it.
 *
 * Four blocks, in the order the decisions are taken: what the product **is** (the editable fields,
 * with the SKU shown and fixed), what it **looks like** (the photo, its own sub-resource), whether it
 * is **in the catalog** (discontinue or reactivate, reversible), and finally deleting it — last,
 * separate, and the only one behind a confirmation, because it is the only one that cannot be undone.
 *
 * The address is the internal identifier and not the SKU. There is no contradiction with ADR-0015:
 * the SKU is the business key an admin *searches* by, and it is immutable precisely because it is not
 * the address. This read resolves discontinued products too, which is what makes reactivating one
 * possible at all.
 */
export default function AdminProductPage() {
  const { t } = useTranslation('backoffice');
  const navigate = useNavigate();
  const params = useParams();
  const [isDeleting, setDeleting] = useState(false);

  // A path segment that is not a number is not a product: `-1` is asked for and answered with the
  // same `404` the server gives for one that does not exist, so there is one way of being missing.
  const id = Number(params.id ?? '');
  const query = useAdminProduct(Number.isSafeInteger(id) ? id : -1);

  return (
    <Container className="py-5">
      <div className="col-lg-8 col-xl-7">
        <Link to={ADMIN_PRODUCTS_PATH} className="d-inline-flex align-items-center gap-1 mb-3">
          <Icon name="arrow_back" size="sm" />
          {t('products.sheet.back')}
        </Link>

        <ResourceBoundary query={query} data={query.data} empty={t('products.sheet.gone')}>
          {(product) => (
            <>
              <div className="d-flex flex-wrap align-items-center gap-3 mb-2">
                <h1 className="h2 mb-0">{product.name}</h1>
                {!product.isActive && (
                  <Badge bg="secondary-subtle" text="secondary-emphasis">
                    {t('products.status.DISCONTINUED')}
                  </Badge>
                )}
              </div>
              <p className="text-body-secondary mb-4 font-monospace">{product.sku}</p>

              <ProductEditForm product={product} />

              <hr className="my-5" />

              <ProductImagePanel product={product} />

              <hr className="my-5" />

              <CatalogStatusPanel product={product} />

              <hr className="my-5" />

              <section>
                <h2 className="h5 mb-1">{t('products.delete.section')}</h2>
                <p className="text-body-secondary small mb-3">{t('products.delete.sectionBody')}</p>
                <Button variant="outline-danger" onClick={() => setDeleting(true)}>
                  {t('products.delete.open')}
                </Button>
              </section>

              {isDeleting && (
                <DeleteProductModal
                  product={product}
                  onClose={() => setDeleting(false)}
                  // The sheet outlives nothing: the product it was showing is gone, so the way out is
                  // the listing, replacing this entry so the back button does not return to a `404`.
                  onDeleted={() => void navigate(ADMIN_PRODUCTS_PATH, { replace: true })}
                />
              )}
            </>
          )}
        </ResourceBoundary>
      </div>
    </Container>
  );
}
