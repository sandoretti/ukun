import { useTranslation } from 'react-i18next';
import { Badge, Button } from 'react-bootstrap';

import { Icon } from '../../../../shared/ui/Icon';
import type { AdminProductType } from '../api/productTypeKeys';

type ProductTypeRowProps = {
  productType: AdminProductType;
  onRename: (productType: AdminProductType) => void;
  onDelete: (productType: AdminProductType) => void;
};

/**
 * One type of the catalog, with the figure that decides what can be done to it.
 *
 * `productCount` counts **discontinued products too** (RF-13): the foreign key does not look at
 * `is_active`, so a type whose products are all withdrawn is still a type in use. Zero is therefore
 * not just a smaller number but a different state — the only one in which the type can be deleted —
 * and it is said in words instead of being left as a `0` the reader has to interpret.
 *
 * Both actions are always drawn, the delete included on a type that cannot be deleted: pressing it is
 * how the reader gets the explanation, and a control that vanishes explains nothing.
 */
export function ProductTypeRow({ productType, onRename, onDelete }: ProductTypeRowProps) {
  const { t } = useTranslation('backoffice');

  return (
    <tr>
      <td>{productType.name}</td>
      <td className="text-tabular-nums text-end">
        {productType.productCount === 0 ? (
          <Badge bg="secondary-subtle" className="text-body-secondary">
            {t('productTypes.table.noProducts')}
          </Badge>
        ) : (
          productType.productCount
        )}
      </td>
      <td>
        <div className="d-flex gap-2 justify-content-end">
          {/* Short in the cell and specific in the name: a table of controls all called «renombrar»
              is a table a screen reader cannot navigate. */}
          <Button
            variant="outline-primary"
            size="sm"
            aria-label={t('productTypes.table.rename', { productType: productType.name })}
            onClick={() => onRename(productType)}
          >
            <Icon name="edit" size="sm" />
          </Button>
          <Button
            variant="outline-danger"
            size="sm"
            aria-label={t('productTypes.table.delete', { productType: productType.name })}
            onClick={() => onDelete(productType)}
          >
            <Icon name="delete" size="sm" />
          </Button>
        </div>
      </td>
    </tr>
  );
}
