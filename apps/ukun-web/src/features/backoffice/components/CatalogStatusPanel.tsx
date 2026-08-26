import { useTranslation } from 'react-i18next';
import { Button } from 'react-bootstrap';

import { ErrorState } from '../../../shared/ui/ErrorState';
import type { AdminProduct } from '../api/adminProductKeys';
import { useDiscontinueProduct, useReactivateProduct } from '../api/adminProductQueries';
import { AdminFormSection } from './AdminFormSection';

type CatalogStatusPanelProps = {
  product: AdminProduct;
};

/**
 * Whether the product is in the catalog, and the one operation that changes it (RF-10, ADR-0013).
 *
 * **Discontinuing is not deleting, and the panel says so in those words.** A discontinued product
 * leaves the public catalog and the stock screens, keeps every order that ever included it, and comes
 * back with one press. Deleting is physical, has its own place further down the sheet, and is only
 * possible for a product nothing has ever referenced.
 *
 * The two operations are idempotent, so nothing here guards against pressing twice: the server
 * answers the same product either way.
 */
export function CatalogStatusPanel({ product }: CatalogStatusPanelProps) {
  const { t } = useTranslation('backoffice');
  const discontinue = useDiscontinueProduct();
  const reactivate = useReactivateProduct();

  const action = product.isActive ? discontinue : reactivate;

  return (
    <AdminFormSection
      title={t('products.catalog.title')}
      description={
        product.isActive ? t('products.catalog.activeBody') : t('products.catalog.discontinuedBody')
      }
    >
      <div aria-live="polite">{action.isError && <ErrorState error={action.error} />}</div>

      <Button
        variant={product.isActive ? 'outline-secondary' : 'primary'}
        onClick={() => action.mutate(product.id)}
        disabled={action.isPending}
      >
        {product.isActive ? t('products.catalog.discontinue') : t('products.catalog.reactivate')}
      </Button>
    </AdminFormSection>
  );
}
