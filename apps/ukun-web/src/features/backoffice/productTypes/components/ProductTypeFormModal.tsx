import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button, Form, Modal } from 'react-bootstrap';

import { toProblem } from '../../../../shared/api/http';
import { applyServerErrors } from '../../../../shared/forms/applyServerErrors';
import { useFieldMessages } from '../../../../shared/hooks/useFieldMessages';
import { ErrorState } from '../../../../shared/ui/ErrorState';
import { TextField } from '../../../../shared/ui/TextField';
import type { AdminProductType } from '../api/productTypeKeys';
import { useCreateProductType, useRenameProductType } from '../api/queries';
import {
  FIELD_MESSAGE_KEYS,
  NAME_MAX,
  productTypeFormSchema,
  toRequest,
  type ProductTypeFormValues,
} from '../productTypeSchema';

const TITLE_ID = 'product-type-form-title';

type ProductTypeFormModalProps = {
  /** The type being renamed, or `undefined` when this is the alta. It is the only difference. */
  productType?: AdminProductType;
  onClose: () => void;
};

/**
 * Creating a type and renaming one, in the same dialog.
 *
 * They are one form because a type has exactly one editable field (RF-13) and the two requests take
 * the same body: two components would be the same eleven lines twice, differing in a verb. It is a
 * modal and not a screen for that same reason — the back-office keeps full pages for forms with
 * groups of fields (the product, the employee) and dialogs for a single one, like `EditQuantityModal`.
 *
 * **A name already taken is painted under the field**, which is where the reader can act on it
 * (RF-13: «se rechaza indicando el campo, no con un error genérico»). The sentence is this
 * namespace's own and not `errors:DUPLICATE_VALUE`: the registry's phrase is written for any unique
 * field of any resource and cannot say which one clashed, while the problem's `field` extension can —
 * so that extension is *read* rather than assumed. Anything other than `name` falls back to the
 * form-level error instead of inventing a field, which would sit in the form state invisible and
 * block every later submit (the reason `applyServerErrors` reports its misses instead of placing them).
 */
export function ProductTypeFormModal({ productType, onClose }: ProductTypeFormModalProps) {
  const { t } = useTranslation('backoffice');
  const messageOf = useFieldMessages('backoffice');

  const create = useCreateProductType();
  const rename = useRenameProductType();

  const isRename = productType !== undefined;
  const isPending = create.isPending || rename.isPending;

  /** A failure nobody could attribute to a field. It never wipes what was typed. */
  const [formError, setFormError] = useState<unknown>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<ProductTypeFormValues>({
    resolver: zodResolver(productTypeFormSchema),
    defaultValues: { name: productType?.name ?? '' },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    const request = toRequest(values);

    try {
      await (isRename
        ? rename.mutateAsync({ id: productType.id, ...request })
        : create.mutateAsync(request));

      onClose();
    } catch (error) {
      const problem = toProblem(error);

      if (problem.code === 'DUPLICATE_VALUE' && problem.extensions.field === 'name') {
        setError('name', { type: 'server', message: 'productTypes.validation.nameTaken' }, { shouldFocus: true });
        return;
      }

      if (!applyServerErrors(error, setError, FIELD_MESSAGE_KEYS)) {
        setFormError(error);
      }
    }
  });

  return (
    <Modal show onHide={onClose} centered aria-labelledby={TITLE_ID}>
      {/* The cross closes, it does not cancel: two controls with the same accessible name in one
          dialog are two things a screen reader cannot tell apart. */}
      <Modal.Header closeButton closeLabel={t('productTypes.form.close')}>
        <Modal.Title id={TITLE_ID} className="h5">
          {isRename ? t('productTypes.form.renameTitle') : t('productTypes.form.createTitle')}
        </Modal.Title>
      </Modal.Header>

      <Form noValidate onSubmit={(event) => void onSubmit(event)}>
        <Modal.Body>
          <TextField
            label={t('productTypes.form.name')}
            hint={t('productTypes.form.nameHint')}
            error={messageOf(errors.name)}
            type="text"
            autoFocus
            spellCheck={false}
            maxLength={NAME_MAX}
            {...register('name')}
          />

          {/* Renaming is safe and saying so is the point: the admin who hesitates here is the one
              who thinks a past order might be rewritten by it (ADR-0011). */}
          {isRename && <p className="text-body-secondary mb-0">{t('productTypes.form.renameNote')}</p>}

          <div aria-live="polite">{formError !== null && <ErrorState error={formError} />}</div>
        </Modal.Body>

        <Modal.Footer>
          <Button variant="light" onClick={onClose} disabled={isPending}>
            {t('productTypes.form.cancel')}
          </Button>
          <Button type="submit" variant="primary" disabled={isPending}>
            {isRename ? t('productTypes.form.save') : t('productTypes.form.create')}
          </Button>
        </Modal.Footer>
      </Form>
    </Modal>
  );
}
