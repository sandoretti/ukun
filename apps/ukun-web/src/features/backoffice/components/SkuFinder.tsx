import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button, Form } from 'react-bootstrap';

import { useFieldMessages } from '../../../shared/hooks/useFieldMessages';
import { TextField } from '../../../shared/ui/TextField';
import { skuFormSchema, type SkuFormValues } from '../stockSchema';

type SkuFinderProps = {
  /** What the field starts with — a SKU carried over from the listing, or the last one searched. */
  defaultSku: string;
  isSearching: boolean;
  /** The SKU resolved to nothing: no active product has it. */
  notFound: boolean;
  onSearch: (sku: string) => void;
};

/**
 * Step one of adding stock: the employee types a SKU and the screen resolves it before there is
 * anything to create (ADR-0015, RF-09).
 *
 * **A SKU that resolves to nothing is a message on the field, not a failure of the screen.** The
 * lookup only resolves active products, so a `404` here is the right answer for something that has
 * been pulled from the catalog (ADR-0013) — and telling the employee that in the place they typed is
 * the difference between "you cannot restock this" and "something went wrong".
 */
export function SkuFinder({ defaultSku, isSearching, notFound, onSearch }: SkuFinderProps) {
  const { t } = useTranslation('backoffice');
  const messageOf = useFieldMessages('backoffice');

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<SkuFormValues>({
    resolver: zodResolver(skuFormSchema),
    defaultValues: { sku: defaultSku },
  });

  const onSubmit = handleSubmit((values) => onSearch(values.sku));

  return (
    <Form noValidate onSubmit={(event) => void onSubmit(event)}>
      <TextField
        label={t('newLine.sku.label')}
        hint={t('newLine.sku.hint')}
        type="text"
        autoComplete="off"
        autoFocus
        // The typed rule comes from zod; "there is no such product" comes from the server. Both land
        // on the same field, which is the one place a field error is painted.
        error={notFound ? t('validation.skuNotFound') : messageOf(errors.sku)}
        {...register('sku')}
      />

      <Button type="submit" variant="primary" disabled={isSearching}>
        {t('newLine.sku.submit')}
      </Button>
    </Form>
  );
}
