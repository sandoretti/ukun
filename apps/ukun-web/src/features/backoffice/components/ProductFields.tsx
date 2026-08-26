import type { ChangeEvent } from 'react';
import { Controller, useFormContext } from 'react-hook-form';
import { useTranslation } from 'react-i18next';

import { useProductTypes } from '../../../shared/api/productTypes';
import { useFieldMessages } from '../../../shared/hooks/useFieldMessages';
import { ErrorState } from '../../../shared/ui/ErrorState';
import { SelectField } from '../../../shared/ui/SelectField';
import { TextField } from '../../../shared/ui/TextField';
import { NAME_MAX, type UpdateProductFormValues } from '../adminProductSchema';

const DESCRIPTION_ROWS = 5;

/**
 * The four fields an admin actually edits about a product: name, description, price and type.
 *
 * They are the whole of `UpdateProductRequest`, and that is the point — the add form is these plus
 * the SKU, and the edit form is these and nothing else. What is missing from both is missing on
 * purpose: the SKU is fixed at creation (ADR-0015), the catalog status is its own audited operation
 * (`:discontinue`/`:reactivate`) and the photo is its own sub-resource (RF-10).
 *
 * It reads the form from context rather than taking a fistful of props, like `AddressFieldset` does:
 * whoever drops it in has already wrapped the form in a `FormProvider`, and the names it registers are
 * the paths the API itself uses in `errors[].field`, so a server rejection lands on the same control
 * as a client one.
 */
export function ProductFields() {
  const { t } = useTranslation('backoffice');
  const messageOf = useFieldMessages('backoffice');
  const types = useProductTypes();
  const {
    control,
    register,
    formState: { errors },
  } = useFormContext<UpdateProductFormValues>();

  return (
    <>
      <TextField
        label={t('products.form.name')}
        error={messageOf(errors.name)}
        type="text"
        maxLength={NAME_MAX}
        autoComplete="off"
        {...register('name')}
      />

      <TextField
        label={t('products.form.description')}
        hint={t('products.form.descriptionHint')}
        error={messageOf(errors.description)}
        as="textarea"
        rows={DESCRIPTION_ROWS}
        {...register('description')}
      />

      {/* Text and not `type="number"`: the amount travels as an exact decimal string (defect #5), and
          a number input would hand back a float and swallow the trailing zero of `10.50`. */}
      <TextField
        label={t('products.form.price')}
        hint={t('products.form.priceHint')}
        error={messageOf(errors.price)}
        type="text"
        inputMode="decimal"
        autoComplete="off"
        {...register('price')}
      />

      {/* The one controlled field of the form, and not by taste: its options are a read that lands
          **after** the product does. An uncontrolled `<select>` handed a value whose `<option>` does
          not exist yet silently falls back to the empty one and never recovers — the sheet of a
          product that has a type would show «pick a type», and the next save would look like a change
          nobody made. Controlled, the value is whatever the form state says on every render, so the
          list arriving is enough. */}
      <Controller
        name="typeId"
        control={control}
        render={({ field }) => (
          <SelectField
            label={t('products.form.type')}
            hint={t('products.form.typeHint')}
            error={messageOf(errors.typeId)}
            disabled={types.isPending}
            name={field.name}
            ref={field.ref}
            onBlur={field.onBlur}
            value={Number.isFinite(field.value) ? String(field.value) : ''}
            // Nothing chosen is `NaN` and not `0` or `''`: zero is a perfectly good identifier, and
            // the schema rejects `NaN` with the sentence this field carries.
            onChange={(event: ChangeEvent<HTMLSelectElement>) =>
              field.onChange(event.target.value === '' ? Number.NaN : Number(event.target.value))
            }
          >
            <option value="">{t('products.form.typePlaceholder')}</option>
            {(types.data ?? []).map((type) => (
              <option key={type.id} value={type.id}>
                {type.name}
              </option>
            ))}
          </SelectField>
        )}
      />

      {/* The closed list failing to load is not a field error: there is nothing to correct, and the
          form cannot be completed until it arrives. It is said where the control is. */}
      {types.isError && <ErrorState error={types.error} />}
    </>
  );
}
