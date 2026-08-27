import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useParams } from 'react-router-dom';
import { Badge, Button } from 'react-bootstrap';

import { ResourceBoundary } from '../../shared/ui/ResourceBoundary';
import { ADMIN_PRODUCTS_PATH } from './adminRoutes';
import { useAdminProduct } from './api/adminProductQueries';
import { AdminFormSection } from './components/AdminFormSection';
import { BackLink } from './components/BackLink';
import { CatalogStatusPanel } from './components/CatalogStatusPanel';
import { DeleteProductModal } from './components/DeleteProductModal';
import { ProductEditForm } from './components/ProductEditForm';
import { ProductImagePanel } from './components/ProductImagePanel';

/**
 * One product of the back-office catalog (RF-10) at `/admin/products/:id`, and everything that can be
 * decided about it.
 *
 * Four blocks in one column, each with its heading and the rule it obeys above its own controls, and
 * the save action anchored to the bottom of the window. The order is deliberate — what the product
 * **is**, what it **looks like**, whether it is **in the catalog**, and last, apart and behind a
 * confirmation, deleting it, because it is the only one that cannot be undone.
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
    <div className="admin-form">
      <div className="admin-form__column">
        <ResourceBoundary query={query} data={query.data} empty={t('products.sheet.gone')}>
          {(product) => (
            <>
              <header className="mb-4">
                <BackLink to={ADMIN_PRODUCTS_PATH} label={t('products.sheet.back')} />

                <p className="overline mb-1">
                  {t('products.sheet.eyebrow')} · {product.sku}
                </p>

                <div className="d-flex flex-wrap align-items-center gap-3 mb-2">
                  <h1 className="h2 mb-0">{product.name}</h1>
                  {!product.isActive && (
                    <Badge bg="secondary-subtle" text="secondary-emphasis">
                      {t('products.status.DISCONTINUED')}
                    </Badge>
                  )}
                </div>

                <p className="text-body-secondary mb-0">{t('products.sheet.lead')}</p>
              </header>

              <hr className="my-0" />

              <ProductEditForm product={product} />

              <hr className="my-0" />

              <ProductImagePanel product={product} />

              <hr className="my-0" />

              <CatalogStatusPanel product={product} />

              <hr className="my-0" />

              <AdminFormSection
                title={t('products.delete.section')}
                description={t('products.delete.sectionBody')}
                tone="danger"
              >
                <Button variant="outline-danger" onClick={() => setDeleting(true)}>
                  {t('products.delete.open')}
                </Button>
              </AdminFormSection>

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
    </div>
  );
}
