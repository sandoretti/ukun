import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router-dom';
import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Button } from 'react-bootstrap';

import { NOT_FOUND, toProblem } from '../../../shared/api/http';
import { formatMoney } from '../../../shared/format/money';
import { applyServerErrors } from '../../../shared/forms/applyServerErrors';
import { HookForm } from '../../../shared/forms/HookForm';
import { useFieldMessages } from '../../../shared/hooks/useFieldMessages';
import { useLocale } from '../../../shared/hooks/useLocale';
import { ErrorState } from '../../../shared/ui/ErrorState';
import { Fieldset } from '../../../shared/ui/Fieldset';
import { ReadOnlyField } from '../../../shared/ui/ReadOnlyField';
import { TextField } from '../../../shared/ui/TextField';
import type { Employment } from '../api/employment';
import { useCreateStockLine, type ProductDetail } from '../api/queries';
import { stockPath } from '../routes';
import {
  FIELD_MESSAGE_KEYS,
  quantityFormSchema,
  toCreateRequest,
  type QuantityFormValues,
} from '../stockSchema';

type NewStockLineFormProps = {
  employment: Employment;
  /** The product the SKU resolved to. There is no form without one. */
  product: ProductDetail;
  onChangeSku: () => void;
};

/**
 * Step two of adding stock: the product is settled, so the only decision left is how many units this
 * store holds.
 *
 * The three ways it can fail are three different things and are said as three different things:
 *
 * - **`409 REFERENTIAL_INTEGRITY`** — the product already has a line here (`ux_store_stock`). Nothing
 *   is wrong with what was typed; the employee is on the wrong screen, so the way out is a link to
 *   the line that exists, not a retry. It leads to the listing filtered by that SKU and not straight
 *   to the dialog, because the problem body of this `409` promises no `lineId` and the SKU is the only
 *   thing the screen knows for certain.
 * - **`404`** — the product was discontinued between resolving it and creating the line (ADR-0013).
 * - **`VALIDATION_FAILED`** — a field the server disagrees with, painted on that field.
 *
 * On success it lands on the listing filtered by the SKU, where the new line is the only row on
 * screen: that is the confirmation, without inventing a toast system for it. It `replace`s, so the
 * back button does not walk into a form that would now answer `409`.
 */
export function NewStockLineForm({ employment, product, onChangeSku }: NewStockLineFormProps) {
  const { t } = useTranslation('backoffice');
  const messageOf = useFieldMessages('backoffice');
  const locale = useLocale();
  const navigate = useNavigate();
  const create = useCreateStockLine(employment.storeId);

  const [duplicate, setDuplicate] = useState(false);
  const [discontinued, setDiscontinued] = useState(false);
  const [formError, setFormError] = useState<unknown>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<QuantityFormValues>({
    resolver: zodResolver(quantityFormSchema),
  });

  const onSubmit = handleSubmit(async (values) => {
    setDuplicate(false);
    setDiscontinued(false);
    setFormError(null);

    try {
      await create.mutateAsync(toCreateRequest(product.sku, values));
      void navigate(stockPath({ sku: product.sku }), { replace: true });
    } catch (error) {
      const problem = toProblem(error);

      if (problem.code === 'REFERENTIAL_INTEGRITY') {
        setDuplicate(true);
      } else if (problem.status === NOT_FOUND) {
        setDiscontinued(true);
      } else if (!applyServerErrors(error, setError, FIELD_MESSAGE_KEYS)) {
        setFormError(error);
      }
    }
  });

  return (
    <HookForm onSubmit={onSubmit}>
      <Fieldset legend={t('newLine.product.title')}>
        <ReadOnlyField label={t('newLine.product.sku')}>{product.sku}</ReadOnlyField>
        <ReadOnlyField label={t('newLine.product.name')}>{product.name}</ReadOnlyField>
        <ReadOnlyField label={t('newLine.product.type')}>{product.typeName}</ReadOnlyField>
        <ReadOnlyField label={t('newLine.product.price')}>
          {formatMoney(product.price, locale)}
        </ReadOnlyField>

        <Button variant="link" className="link-secondary px-0" onClick={onChangeSku}>
          {t('newLine.product.change')}
        </Button>
      </Fieldset>

      <TextField
        label={t('newLine.quantity.label', { store: employment.storeName })}
        hint={t('newLine.quantity.hint')}
        type="number"
        min={0}
        step={1}
        inputMode="numeric"
        autoComplete="off"
        error={messageOf(errors.quantity)}
        {...register('quantity', { valueAsNumber: true })}
      />

      <div aria-live="polite">
        {duplicate && (
          <Alert variant="warning">
            <h2 className="h6">{t('newLine.duplicate.title')}</h2>
            <p>{t('newLine.duplicate.body', { product: product.name, store: employment.storeName })}</p>
            <Link to={stockPath({ sku: product.sku })} className="alert-link">
              {t('newLine.duplicate.link')}
            </Link>
          </Alert>
        )}
        {discontinued && <Alert variant="warning">{t('validation.skuNotFound')}</Alert>}
        {formError !== null && <ErrorState error={formError} />}
      </div>

      <Button type="submit" variant="primary" disabled={create.isPending}>
        {t('newLine.submit')}
      </Button>
    </HookForm>
  );
}
