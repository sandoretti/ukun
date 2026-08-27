import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button, Modal } from 'react-bootstrap';

import { applyDuplicateValue, applyServerErrors } from '../../../../shared/forms/applyServerErrors';
import { HookForm } from '../../../../shared/forms/HookForm';
import { useFieldMessages } from '../../../../shared/hooks/useFieldMessages';
import { Dialog } from '../../../../shared/ui/Dialog';
import { ErrorState } from '../../../../shared/ui/ErrorState';
import { TextField } from '../../../../shared/ui/TextField';
import type { AdminProductType } from '../api/productTypeKeys';
import { useCreateProductType, useRenameProductType } from '../api/queries';
import {
  DUPLICATE_MESSAGE_KEYS,
  FIELD_MESSAGE_KEYS,
  NAME_MAX,
  productTypeFormSchema,
  toRequest,
  type ProductTypeFormValues,
} from '../productTypeSchema';

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
 * (RF-13: «se rechaza indicando el campo, no con un error genérico»). Which field that is comes from
 * the problem's own `field` extension and the map in `productTypeSchema`; `applyDuplicateValue` owns
 * the reading, so this screen and the product alta answer the same rejection the same way.
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
      if (applyDuplicateValue(error, setError, DUPLICATE_MESSAGE_KEYS)) {
        return;
      }

      if (!applyServerErrors(error, setError, FIELD_MESSAGE_KEYS)) {
        setFormError(error);
      }
    }
  });

  return (
    <Dialog
      title={isRename ? t('productTypes.form.renameTitle') : t('productTypes.form.createTitle')}
      closeLabel={t('productTypes.form.close')}
      onClose={onClose}
    >
      <HookForm onSubmit={onSubmit}>
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
      </HookForm>
    </Dialog>
  );
}
