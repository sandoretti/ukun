import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Table } from 'react-bootstrap';

import { Icon } from '../../../shared/ui/Icon';
import { PageHeader } from '../../../shared/ui/PageHeader';
import { QueryBoundary } from '../../../shared/ui/QueryBoundary';
import { useAdminProductTypes } from './api/queries';
import { DeleteProductTypeModal } from './components/DeleteProductTypeModal';
import { ProductTypeFormModal } from './components/ProductTypeFormModal';
import { ProductTypeRow } from './components/ProductTypeRow';

/** Which dialog is open: the alta, one type being renamed, or nothing. */
type Editing = { kind: 'new' } | { kind: 'rename'; id: number } | null;

/**
 * The catalog's types (RF-13) at `/admin/product-types`.
 *
 * **This requirement does not come from the legacy system**, and that is the reason it exists: there
 * `tipo_producto` is seeded once and `producto.tipo` is `NOT NULL`, so adding a category means an
 * `INSERT` by hand against the database. It is the first screen an administrator needs on a fresh
 * install — a product cannot be created without a type — which is why the roadmap puts it before the
 * products one and why it sits above it in the rail.
 *
 * **Nothing of this screen lives in the URL**, unlike the stock, the catalog and the staff. The rule
 * is that a listing's filters, sort and page are query params, and this listing has none of the
 * three: `GET /api/admin/product-types` takes no parameters because the set is small and closed, and
 * the server sorts it. There is nothing here that a link could carry.
 *
 * The three writes happen in dialogs rather than in screens of their own: a type has exactly one
 * editable field, and the back-office keeps full pages for forms with groups of fields.
 *
 * Both dialogs are addressed **by id and resolved against the list on every render**, never by the
 * row object captured when the button was pressed. It is what keeps the refusal to delete honest: the
 * `productCount` the dialog reasons about is the one the last answer from the server carried, and a
 * type somebody else deleted meanwhile takes its own dialog off the screen instead of operating on a
 * row that no longer exists.
 */
export default function ProductTypesPage() {
  const { t } = useTranslation('backoffice');
  const query = useAdminProductTypes();

  const [editing, setEditing] = useState<Editing>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);

  /**
   * Read here and not down in the JSX. TanStack Query only re-renders for the fields a render
   * actually touched, and a read that fails after the first render touched nothing but `data` would
   * leave the screen spinning over an error that already came back — the trap `EmployeesPage`
   * documents from the other side.
   */
  const productTypes = query.data;
  const status = { isPending: query.isPending, isError: query.isError, error: query.error };

  const renaming =
    editing?.kind === 'rename' ? productTypes?.find((type) => type.id === editing.id) : undefined;
  const deleting = productTypes?.find((type) => type.id === deletingId);

  return (
    <>
      <PageHeader title={t('productTypes.title')} lead={t('productTypes.lead')}>
        {/* A button and not a link: what it opens is a dialog, not a route. */}
        <Button
          variant="primary"
          className="d-inline-flex align-items-center gap-1"
          onClick={() => setEditing({ kind: 'new' })}
        >
          <Icon name="add" size="sm" />
          {t('productTypes.add')}
        </Button>
      </PageHeader>

      <p className="text-body-secondary" aria-live="polite">
        {productTypes === undefined ? null : t('productTypes.results', { count: productTypes.length })}
      </p>

      <QueryBoundary query={status} data={productTypes} empty={t('productTypes.empty')}>
        {(rows) => (
          <Table hover responsive className="align-middle">
            <caption className="visually-hidden">{t('productTypes.table.caption')}</caption>
            <thead>
              <tr>
                <th scope="col">{t('productTypes.table.name')}</th>
                <th scope="col" className="text-end">
                  {t('productTypes.table.productCount')}
                </th>
                <th scope="col" className="text-end">
                  {t('productTypes.table.actions')}
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((productType) => (
                <ProductTypeRow
                  key={productType.id}
                  productType={productType}
                  onRename={(type) => setEditing({ kind: 'rename', id: type.id })}
                  onDelete={(type) => setDeletingId(type.id)}
                />
              ))}
            </tbody>
          </Table>
        )}
      </QueryBoundary>

      {editing?.kind === 'new' && <ProductTypeFormModal onClose={() => setEditing(null)} />}

      {renaming !== undefined && (
        // Keyed by the type: the form is uncontrolled, so a different row has to arrive as a
        // different component for its default value to be the name being renamed.
        <ProductTypeFormModal
          key={renaming.id}
          productType={renaming}
          onClose={() => setEditing(null)}
        />
      )}

      {deleting !== undefined && (
        <DeleteProductTypeModal productType={deleting} onClose={() => setDeletingId(null)} />
      )}
    </>
  );
}
