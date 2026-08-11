import { useEffect, useId, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useFormContext, useWatch } from 'react-hook-form';
import { Button, Col, Row } from 'react-bootstrap';

import { useFieldMessages } from '../../../shared/hooks/useFieldMessages';
import { Fieldset } from '../../../shared/ui/Fieldset';
import { SelectField } from '../../../shared/ui/SelectField';
import { TextField } from '../../../shared/ui/TextField';
import { CARD_BRANDS, MONTH_MAX, MONTH_MIN, YEAR_MAX, YEAR_MIN, type ProfileFormValues } from '../profileSchema';
import { PaymentMethodCard } from './PaymentMethodCard';

const MONTHS = Array.from({ length: MONTH_MAX }, (_, index) => index + MONTH_MIN);

/** The card's fields in the order they are read, which is also the order a focus jump follows. */
const CARD_FIELDS = ['last4', 'brand', 'expMonth', 'expYear'] as const;

/**
 * The payment method: the card as a card, and the four controls that change it folded away until
 * somebody actually means to change it.
 *
 * **Folding fields away has a cost, and it is paid here.** A collapsed field holding an error is a
 * form that will not submit and does not say why, so the block opens itself the moment anything lands
 * on `card.*` — the zod schema or the API — and the focus goes to the field at fault. That is what
 * the two effects below do, and they are two and not one on purpose: `setFocus` on a control that is
 * not mounted yet does nothing at all, so opening and focusing cannot happen in the same pass.
 */
export function CardFieldset() {
  const { t } = useTranslation('profile');
  const messageOf = useFieldMessages('profile');
  const {
    control,
    register,
    setFocus,
    trigger,
    formState: { errors },
  } = useFormContext<ProfileFormValues>();

  const card = useWatch({ control, name: 'card' });
  const [openedByReader, setOpenedByReader] = useState(false);
  const wasEditing = useRef(false);
  const toggleRef = useRef<HTMLButtonElement>(null);
  const panelId = useId();

  const erroredField = CARD_FIELDS.find((name) => errors.card?.[name] !== undefined);
  const errorPath = erroredField === undefined ? undefined : (`card.${erroredField}` as const);

  // 1. Open because somebody asked, or because there is something inside that has to be seen. It is
  //    derived and not stored on purpose: an error cannot be closed away — while it is there the
  //    panel stays open, and it reopens by itself if the server sends one back.
  const editing = openedByReader || errorPath !== undefined;

  // 2. And once the panel is open, the caret goes where the problem is — or to the first field, when
  //    the block was opened on purpose. Only on the way in: re-running this while the reader types
  //    would yank the caret back out of whatever field they were fixing.
  useEffect(() => {
    if (editing && !wasEditing.current) {
      setFocus(errorPath ?? 'card.last4');
    }
    wasEditing.current = editing;
  }, [editing, errorPath, setFocus]);

  async function closeEditor() {
    // Never fold an invalid card out of sight: if it does not validate, the error appears and the
    // rule above keeps the panel open with the message next to its field.
    if (await trigger('card')) {
      setOpenedByReader(false);
      toggleRef.current?.focus();
    }
  }

  return (
    <Fieldset legend={t('card.legend')} description={t('card.description')} className="settings-section">
      <PaymentMethodCard card={card} />

      {/* La promesa, junto a la tarjeta y no en letra pequeña al final: es la diferencia entre que se
          deduzca de la ausencia de un campo y que se diga. */}
      <p className="text-body-secondary small mt-2 mb-3">{t('card.noCvv')}</p>

      {!editing && (
        <Button
          ref={toggleRef}
          type="button"
          variant="outline-primary"
          size="sm"
          aria-expanded={false}
          aria-controls={panelId}
          onClick={() => setOpenedByReader(true)}
        >
          {t('card.change')}
        </Button>
      )}

      {editing && (
        <div id={panelId}>
          <Row>
            <Col md={4}>
              <TextField
                label={t('card.last4')}
                error={messageOf(errors.card?.last4)}
                type="text"
                inputMode="numeric"
                maxLength={4}
                // Deliberately not `cc-number`: asking the browser to autofill a card number into a
                // four-digit field is inviting back in exactly what this contract keeps out.
                autoComplete="off"
                {...register('card.last4')}
              />
            </Col>
            <Col md={4}>
              <SelectField
                label={t('card.brand')}
                error={messageOf(errors.card?.brand)}
                autoComplete="cc-type"
                {...register('card.brand')}
              >
                {CARD_BRANDS.map((brand) => (
                  <option key={brand} value={brand}>
                    {t(`card.brands.${brand}`)}
                  </option>
                ))}
              </SelectField>
            </Col>
            <Col md={2}>
              {/* Mes y año, no una fecha: una tarjeta caduca a lo largo de todo un mes (docs/10 §2). */}
              <SelectField
                label={t('card.expMonth')}
                error={messageOf(errors.card?.expMonth)}
                autoComplete="cc-exp-month"
                {...register('card.expMonth', {
                  setValueAs: (value: string) => (value === '' ? Number.NaN : Number(value)),
                })}
              >
                {MONTHS.map((month) => (
                  <option key={month} value={month}>
                    {String(month).padStart(2, '0')}
                  </option>
                ))}
              </SelectField>
            </Col>
            <Col md={2}>
              <TextField
                label={t('card.expYear')}
                error={messageOf(errors.card?.expYear)}
                type="number"
                inputMode="numeric"
                min={YEAR_MIN}
                max={YEAR_MAX}
                autoComplete="cc-exp-year"
                {...register('card.expYear', { valueAsNumber: true })}
              />
            </Col>
          </Row>

          <Button type="button" variant="outline-secondary" size="sm" onClick={() => void closeEditor()}>
            {t('card.done')}
          </Button>
        </div>
      )}
    </Fieldset>
  );
}
