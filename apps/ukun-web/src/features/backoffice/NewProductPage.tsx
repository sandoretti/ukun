import { useState } from 'react';
import { FormProvider, useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router-dom';
import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Button, Container, Form } from 'react-bootstrap';

import { NOT_FOUND, toProblem } from '../../shared/api/http';
import { applyServerErrors } from '../../shared/forms/applyServerErrors';
import { useFieldMessages } from '../../shared/hooks/useFieldMessages';
import { ErrorState } from '../../shared/ui/ErrorState';
import { Icon } from '../../shared/ui/Icon';
import { TextField } from '../../shared/ui/TextField';
import {
  createProductFormSchema,
  FIELD_MESSAGE_KEYS,
  toCreateRequest,
  type CreateProductFormValues,
} from './adminProductSchema';
import { adminProductPath, adminProductsPath, ADMIN_PRODUCTS_PATH } from './adminRoutes';
import { useCreateProduct } from './api/adminProductQueries';
import { ProductFields } from './components/ProductFields';

/** The SKU as long as the contract allows it (`Sku`, 3–32), so the browser stops before the server does. */
const SKU_MAX = 32;

/**
 * Creating a product (RF-10) at `/admin/products/new`.
 *
 * **The SKU is typed here and never again** (ADR-0015): it is the business identifier an employee
 * searches stock by and the one printed on the physical label, so rewriting it would silently change
 * which product a label already in the warehouse points at. Changing a SKU is creating another
 * product, and the edit screen shows it as the read-only value it is.
 *
 * **The product is born active and without a photo**, which is the contract's own shape for
 * `CreateProductRequest`. That is why this screen lands on the sheet of what it just created rather
 * than back on the listing: the photo is the natural next step and it lives there. It `replace`s, so
 * the back button does not walk into a form that would now answer `409`.
 *
 * The SKU already in use is the one failure that gets a way out rather than a sentence: it leads to
 * the listing filtered by that SKU with `status=ALL`, because the product holding it may well be a
 * discontinued one — a SKU belongs to one product for life, reused or not.
 */
export default function NewProductPage() {
  const { t } = useTranslation('backoffice');
  const messageOf = useFieldMessages('backoffice');
  const navigate = useNavigate();
  const create = useCreateProduct();

  const [duplicateSku, setDuplicateSku] = useState<string | null>(null);
  const [formError, setFormError] = useState<unknown>(null);

  const form = useForm<CreateProductFormValues>({
    resolver: zodResolver(createProductFormSchema),
    defaultValues: { sku: '', name: '', description: '', price: '' },
  });

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = form;

  const onSubmit = handleSubmit(async (values) => {
    setDuplicateSku(null);
    setFormError(null);

    try {
      const created = await create.mutateAsync(toCreateRequest(values));
      void navigate(adminProductPath(created.id), { replace: true });
    } catch (error) {
      const problem = toProblem(error);

      if (problem.code === 'DUPLICATE_VALUE') {
        setDuplicateSku(values.sku);
        setError('sku', { type: 'server', message: 'products.validation.skuTaken' }, { shouldFocus: true });
      } else if (problem.status === NOT_FOUND) {
        // The only identifier this request carries besides the SKU is the type, and the SKU cannot be
        // missing — it is being created. So a `404` here is a type that stopped existing while the
        // form was open, which is a field to correct and not a failed screen.
        setError('typeId', { type: 'server', message: 'products.validation.typeGone' }, { shouldFocus: true });
      } else if (!applyServerErrors(error, setError, FIELD_MESSAGE_KEYS)) {
        setFormError(error);
      }
    }
  });

  return (
    <Container className="py-5">
      <div className="col-lg-8 col-xl-6">
        <Link to={ADMIN_PRODUCTS_PATH} className="d-inline-flex align-items-center gap-1 mb-3">
          <Icon name="arrow_back" size="sm" />
          {t('products.new.back')}
        </Link>

        <h1 className="h2 mb-2">{t('products.new.title')}</h1>
        <p className="text-body-secondary mb-4">{t('products.new.lead')}</p>

        <FormProvider {...form}>
          <Form noValidate onSubmit={(event) => void onSubmit(event)}>
            <TextField
              label={t('products.form.sku')}
              hint={t('products.form.skuHint')}
              error={messageOf(errors.sku)}
              type="text"
              maxLength={SKU_MAX}
              autoComplete="off"
              autoFocus
              {...register('sku')}
            />

            <ProductFields />

            <div aria-live="polite">
              {duplicateSku !== null && (
                <Alert variant="warning">
                  <h2 className="h6">{t('products.new.duplicate.title')}</h2>
                  <p>{t('products.new.duplicate.body', { sku: duplicateSku })}</p>
                  <Link
                    to={adminProductsPath({ sku: duplicateSku, status: 'ALL' })}
                    className="alert-link"
                  >
                    {t('products.new.duplicate.link')}
                  </Link>
                </Alert>
              )}
              {formError !== null && <ErrorState error={formError} />}
            </div>

            <Button type="submit" variant="primary" disabled={create.isPending}>
              {t('products.new.submit')}
            </Button>
          </Form>
        </FormProvider>
      </div>
    </Container>
  );
}
