import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Container, Form } from 'react-bootstrap';

import { AdminFormSection } from './AdminFormSection';
import { ProductFields } from './ProductFields';

type ProductFormProps = {
  /** What the block is called here: creating a product and editing one are not the same sentence. */
  description: string;
  /**
   * What identifies the product. It is the one real difference between the two screens: the add form
   * types a SKU in, and the sheet shows the one it was given and refuses to touch it (ADR-0015). It
   * travels as a node rather than as a `isEditable` flag, because what changes is the control itself.
   */
  identity: ReactNode;
  /** What went wrong beyond a field — a taken SKU, a failed write. Announced, never silent. */
  feedback?: ReactNode;
  /** Where the form stands, for the anchored bar. */
  status: string;
  /** The actions of that bar. The one that commits goes last, which is where the eye ends up. */
  actions: ReactNode;
  onSubmit: () => void;
};

/**
 * The product form, shared whole by the two screens that write one.
 *
 * What they have in common is everything except the identity: the same four editable fields
 * (`ProductFields`, which are exactly `UpdateProductRequest`), the same block with its heading and
 * its rule, the same anchored bar. Keeping that in one place is what stops the two screens from
 * drifting into two different forms for the same product — a field added here shows up in both, and
 * so does the wording of what they are for.
 *
 * **It does not own the form state.** Each screen creates its own `useForm` with its own schema and
 * wraps this in a `FormProvider`: the add form validates a SKU that the sheet does not have, and the
 * sheet follows the server's answer with `values` in a way an add form has no use for. What is shared
 * is the form, not the two different things being decided with it.
 */
export function ProductForm({
  description,
  identity,
  feedback,
  status,
  actions,
  onSubmit,
}: ProductFormProps) {
  const { t } = useTranslation('backoffice');

  return (
    <Form noValidate onSubmit={onSubmit}>
      <AdminFormSection title={t('products.sheet.details')} description={description}>
        {identity}

        <ProductFields />

        <div aria-live="polite">{feedback}</div>
      </AdminFormSection>

      {/* The action bar, anchored to the bottom of the window. A form that does not fit in a
          viewport cannot keep its save button at the end of the document: the way to it would run
          past «delete this product», which is the one control nobody should pass through on their way
          to saving a price. It sits inside the `<form>` even though it is painted down there, which
          is what lets the button stay a plain `submit` — Enter key included. */}
      <div className="admin-form__bar">
        <Container className="d-flex flex-wrap align-items-center justify-content-between gap-3 py-3">
          {/* Announced and not only coloured: "saved" is something a screen reader hears too. */}
          <p className="overline mb-0" aria-live="polite">
            {status}
          </p>
          <div className="d-flex flex-wrap gap-2">{actions}</div>
        </Container>
      </div>
    </Form>
  );
}
