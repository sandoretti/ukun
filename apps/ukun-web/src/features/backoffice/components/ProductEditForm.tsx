import { useState } from 'react';
import { FormProvider, useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Button, Form } from 'react-bootstrap';

import { NOT_FOUND, toProblem } from '../../../shared/api/http';
import { applyServerErrors } from '../../../shared/forms/applyServerErrors';
import { ErrorState } from '../../../shared/ui/ErrorState';
import { Fieldset } from '../../../shared/ui/Fieldset';
import { ReadOnlyField } from '../../../shared/ui/ReadOnlyField';
import {
  FIELD_MESSAGE_KEYS,
  toUpdateRequest,
  updateProductFormSchema,
  type UpdateProductFormValues,
} from '../adminProductSchema';
import type { AdminProduct } from '../api/adminProductKeys';
import { useUpdateProduct } from '../api/adminProductQueries';
import { ProductFields } from './ProductFields';

type ProductEditFormProps = {
  product: AdminProduct;
};

/**
 * Editing what an admin actually edits about a product: name, description, price and type.
 *
 * **The SKU is here as a value and not as a control** (ADR-0015): it is shown because whoever is
 * looking at this sheet needs to know which product it is, and it cannot be typed into because
 * changing it would silently repoint a label already printed. The hint says so, rather than leaving a
 * greyed-out box for the reader to fight with.
 *
 * **`isActive` is not a checkbox here either.** Taking a product out of the catalog is an audited
 * decision with its own operation and its own panel; buried in this form it would ride along inside a
 * price change, and nobody would be able to say afterwards when it happened or why.
 *
 * There is no version and no conflict panel: editing a product is last-write-wins by explicit
 * decision (`docs/10-modelo-de-datos.md` §2), unlike a stock line.
 */
export function ProductEditForm({ product }: ProductEditFormProps) {
  const { t } = useTranslation('backoffice');
  const update = useUpdateProduct();

  const [saved, setSaved] = useState(false);
  const [formError, setFormError] = useState<unknown>(null);

  const form = useForm<UpdateProductFormValues>({
    resolver: zodResolver(updateProductFormSchema),
    // `values` and not `defaultValues`: the sheet re-reads itself after every write, and following
    // the server is what keeps the form showing what was actually saved.
    values: {
      name: product.name,
      description: product.description ?? '',
      price: product.price,
      typeId: product.typeId,
    },
  });

  const {
    handleSubmit,
    setError,
    formState: { isDirty },
  } = form;

  const onSubmit = handleSubmit(async (values) => {
    setSaved(false);
    setFormError(null);

    try {
      await update.mutateAsync({ id: product.id, ...toUpdateRequest(values) });
      setSaved(true);
    } catch (error) {
      const problem = toProblem(error);

      if (problem.status === NOT_FOUND && problem.extensions.resource !== 'product') {
        // The other identifier this request carries: a type that stopped existing while the sheet was
        // open. It is a field to correct, not a failed screen.
        setError('typeId', { type: 'server', message: 'products.validation.typeGone' }, { shouldFocus: true });
      } else if (!applyServerErrors(error, setError, FIELD_MESSAGE_KEYS)) {
        setFormError(error);
      }
    }
  });

  return (
    <FormProvider {...form}>
      <Form noValidate onSubmit={(event) => void onSubmit(event)}>
        <Fieldset legend={t('products.sheet.details')}>
          <ReadOnlyField label={t('products.form.sku')} hint={t('products.form.skuFixed')}>
            <span className="font-monospace">{product.sku}</span>
          </ReadOnlyField>

          <ProductFields />
        </Fieldset>

        {/* Saving is announced where it happened, and it is announced at all: a form that answers a
            successful write with nothing leaves the reader pressing the button again. */}
        <div aria-live="polite">
          {saved && !isDirty && <Alert variant="success">{t('products.sheet.saved')}</Alert>}
          {formError !== null && <ErrorState error={formError} />}
        </div>

        <Button type="submit" variant="primary" disabled={update.isPending}>
          {t('products.sheet.save')}
        </Button>
      </Form>
    </FormProvider>
  );
}
