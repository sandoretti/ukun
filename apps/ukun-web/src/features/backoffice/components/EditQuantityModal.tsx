import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button, Modal } from 'react-bootstrap';

import { NOT_FOUND, toProblem } from '../../../shared/api/http';
import { HookForm } from '../../../shared/forms/HookForm';
import { useFieldMessages } from '../../../shared/hooks/useFieldMessages';
import { ErrorState } from '../../../shared/ui/ErrorState';
import { TextField } from '../../../shared/ui/TextField';
import { useUpdateStockLine } from '../api/queries';
import type { StockedLine } from '../api/stockKeys';
import { quantityFormSchema, toUpdateRequest, type QuantityFormValues } from '../stockSchema';
import { VersionConflictPanel } from './VersionConflictPanel';

const TITLE_ID = 'edit-quantity-title';

type EditQuantityModalProps = {
  storeId: number;
  /**
   * The line as the **last answer from the server** describes it — looked up by `StockedView` on
   * every render, never a copy this dialog keeps. `undefined` means somebody else deleted it while
   * this was open.
   */
  line: StockedLine | undefined;
  /** The listing is being read again, so the figures on screen are not the final ones yet. */
  isRefreshing: boolean;
  onClose: () => void;
};

/** The line is gone: there is nothing to save and nothing to retry, so there is one way out. */
function GoneBody({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation('backoffice');

  return (
    <>
      <Modal.Body>{t('edit.gone')}</Modal.Body>
      <Modal.Footer>
        <Button variant="light" onClick={onClose}>
          {t('edit.close')}
        </Button>
      </Modal.Footer>
    </>
  );
}

/**
 * Changing the units of one line, and the whole of ADR-0008's optimistic path on screen.
 *
 * The dialog never closes on a conflict and never reapplies by itself; the two ways out are the two
 * buttons of `VersionConflictPanel`, and both are the employee's decision. The `version` that travels
 * with a retry is the **new** one, because it is read from `line`, which by then is the row the
 * server sent back — not a copy taken when the dialog opened.
 */
export function EditQuantityModal({ storeId, line, isRefreshing, onClose }: EditQuantityModalProps) {
  const { t } = useTranslation('backoffice');

  return (
    <Modal show onHide={onClose} centered aria-labelledby={TITLE_ID}>
      {/* The cross closes, it does not cancel: two controls with the same accessible name in one
          dialog are two things a screen reader cannot tell apart. */}
      <Modal.Header closeButton closeLabel={t('edit.close')}>
        <Modal.Title id={TITLE_ID} className="h5">
          {t('edit.title')}
        </Modal.Title>
      </Modal.Header>

      {line === undefined ? (
        <GoneBody onClose={onClose} />
      ) : (
        <EditQuantityForm storeId={storeId} line={line} isRefreshing={isRefreshing} onClose={onClose} />
      )}
    </Modal>
  );
}

type EditQuantityFormProps = {
  storeId: number;
  line: StockedLine;
  isRefreshing: boolean;
  onClose: () => void;
};

function EditQuantityForm({ storeId, line, isRefreshing, onClose }: EditQuantityFormProps) {
  const { t } = useTranslation('backoffice');
  const messageOf = useFieldMessages('backoffice');
  const update = useUpdateStockLine(storeId);

  /** What the employee typed and tried to save — the figure the conflict panel offers to reapply. */
  const [attempted, setAttempted] = useState<number | null>(null);
  /** A conflict is a mode, not just the last error: it survives the retry that is trying to resolve it. */
  const [conflicted, setConflicted] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<QuantityFormValues>({
    resolver: zodResolver(quantityFormSchema),
    // `defaultValues` and not `values`: the conflict re-reads the line, and following the cache here
    // would overwrite what the employee typed with the figure they are being asked to compare it to.
    defaultValues: { quantity: line.quantity },
  });

  const save = (quantity: number) => {
    setAttempted(quantity);
    update.mutate(
      { lineId: line.lineId, ...toUpdateRequest({ quantity }, line.version) },
      {
        onSuccess: onClose,
        onError: (error) => setConflicted(toProblem(error).code === 'VERSION_CONFLICT'),
      },
    );
  };

  const onSubmit = handleSubmit((values) => save(values.quantity));

  const problem = update.error === null ? undefined : toProblem(update.error);

  if (!conflicted && problem?.status === NOT_FOUND) {
    return <GoneBody onClose={onClose} />;
  }

  if (conflicted && attempted !== null) {
    return (
      <Modal.Body>
        <VersionConflictPanel
          serverQuantity={line.quantity}
          yourQuantity={attempted}
          isBusy={isRefreshing || update.isPending}
          onKeepServer={onClose}
          onApplyMine={() => save(attempted)}
        />
      </Modal.Body>
    );
  }

  return (
    <HookForm onSubmit={onSubmit}>
      <Modal.Body>
        <p className="text-body-secondary">
          {t('edit.product', { product: line.productName, sku: line.sku })}
        </p>

        <TextField
          label={t('edit.quantity')}
          hint={t('edit.quantityHint')}
          type="number"
          min={0}
          step={1}
          inputMode="numeric"
          autoFocus
          error={messageOf(errors.quantity)}
          {...register('quantity', { valueAsNumber: true })}
        />

        {/* A failed write never wipes what is on screen: it is announced here, over the form that
            still holds what was typed. */}
        <div aria-live="polite">{problem !== undefined && <ErrorState error={update.error} />}</div>
      </Modal.Body>

      <Modal.Footer>
        <Button variant="light" onClick={onClose} disabled={update.isPending}>
          {t('edit.cancel')}
        </Button>
        <Button type="submit" variant="primary" disabled={update.isPending}>
          {t('edit.save')}
        </Button>
      </Modal.Footer>
    </HookForm>
  );
}
