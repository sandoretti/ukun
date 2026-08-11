import { useEffect, useId, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useFormContext, useWatch } from 'react-hook-form';
import { Button, Col, Form, Row } from 'react-bootstrap';

import { useFieldMessages } from '../../../shared/hooks/useFieldMessages';
import { cardDigits, detectCardBrand, isPlausibleCardNumber } from '../../../shared/payments/card';
import { Fieldset } from '../../../shared/ui/Fieldset';
import { PaymentMethodCard } from '../../../shared/ui/PaymentMethodCard';
import { SelectField } from '../../../shared/ui/SelectField';
import { TextField } from '../../../shared/ui/TextField';
import { MONTH_MAX, MONTH_MIN, YEAR_MAX, cardToSave, type ProfileFormValues } from '../profileSchema';

const MONTHS = Array.from({ length: MONTH_MAX }, (_, index) => index + MONTH_MIN);

/** How far ahead the year list goes. Cards are issued for a few years, not for the next century. */
const YEARS_AHEAD = 20;

/** The longest printed number is nineteen digits in five groups: 19 + 4 spaces. */
const NUMBER_MAX_LENGTH = 23;

/**
 * The payment method: the card as a card, and —folded away until somebody means to change it— the
 * number of the new one.
 *
 * **The number is asked for, and it never leaves the browser.** What the contract stores is what it
 * has always stored, four digits and a brand (`docs/08-identidad-y-seguridad.md` §5); the rest of the
 * number is dropped in `toRequest`, which is typed to return the contract's own request and therefore
 * has nowhere to put it. Nobody is asked for a CVV, here or anywhere else: that one is forbidden to
 * store under any circumstance, so there is nothing to collect it for.
 *
 * The brand is **not** asked for either. It is written in the first digits of the number (the IIN),
 * and a form that makes somebody choose "Visa" from a list right after typing a number starting with
 * a 4 is asking a question it already knows the answer to.
 *
 * **Folding fields away has a cost, and it is paid here.** A collapsed field holding an error is a
 * form that will not submit and does not say why, so the block opens itself the moment anything lands
 * on the card — and the focus goes to the control at fault, which for a rejected `last4` is the
 * number field, because that is where a reader can do something about it.
 */
export function CardFieldset() {
  const { t } = useTranslation(['profile', 'common']);
  const messageOf = useFieldMessages('profile');
  const {
    control,
    register,
    setFocus,
    trigger,
    formState: { errors },
  } = useFormContext<ProfileFormValues>();

  const card = useWatch({ control, name: 'card' });
  const cardNumber = useWatch({ control, name: 'cardNumber' });
  const [openedByReader, setOpenedByReader] = useState(false);
  const wasEditing = useRef(false);
  const toggleRef = useRef<HTMLButtonElement>(null);
  const panelId = useId();
  const expiryLabelId = useId();

  /**
   * Every way the card can be wrong, in reading order, each pointing at the control that can fix it.
   * `card.last4` and `card.brand` have no control of their own any more —they are read off the
   * number— so a rejection of either is shown and focused on the number field.
   */
  const issues = [
    { error: errors.cardNumber, focus: 'cardNumber' as const },
    { error: errors.card?.last4, focus: 'cardNumber' as const },
    { error: errors.card?.brand, focus: 'cardNumber' as const },
    { error: errors.card?.expMonth, focus: 'card.expMonth' as const },
    { error: errors.card?.expYear, focus: 'card.expYear' as const },
  ];
  const firstIssue = issues.find(({ error }) => error !== undefined);

  // Open because somebody asked, or because there is something inside that has to be seen. Derived
  // and not stored: an error cannot be closed away, and it reopens the block by itself if the server
  // sends one back.
  const editing = openedByReader || firstIssue !== undefined;

  // And once the panel is open, the caret goes where the problem is — or to the number, when the
  // block was opened on purpose. Only on the way in: re-running this while the reader types would
  // yank the caret out of whatever field they were fixing.
  useEffect(() => {
    if (editing && !wasEditing.current) {
      setFocus(firstIssue?.focus ?? 'cardNumber');
    }
    wasEditing.current = editing;
    // `firstIssue` is rebuilt on every render; what this depends on is where it points, and that is
    // a string.
  }, [editing, firstIssue?.focus, setFocus]);

  async function closeEditor() {
    // Never fold an invalid card out of sight: if it does not validate, the error appears and the
    // rule above keeps the panel open with the message next to its field.
    if (await trigger(['cardNumber', 'card'])) {
      setOpenedByReader(false);
      toggleRef.current?.focus();
    }
  }

  const digits = cardDigits(cardNumber);
  const typedBrand = isPlausibleCardNumber(digits) ? detectCardBrand(digits) : undefined;

  // A year already on the card can sit outside the offered range; it is still the card's year.
  const thisYear = new Date().getFullYear();
  const years = Array.from({ length: YEARS_AHEAD + 1 }, (_, index) => thisYear + index)
    .concat(card.expYear <= YEAR_MAX ? [card.expYear] : [])
    .filter((year, index, all) => all.indexOf(year) === index)
    .sort((left, right) => left - right);

  return (
    <Fieldset legend={t('card.legend')} description={t('card.description')} className="settings-section">
      {/* Lo que se guardará: mientras se teclea un número válido, la tarjeta de arriba ya enseña sus
          cuatro últimos y su marca, que es la confirmación de que se ha leído bien. */}
      <PaymentMethodCard card={cardToSave(card, cardNumber)} />

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
            <Col md={7}>
              {/* Vacío siempre: no hay número que devolver, porque nadie guardó ninguno. Y el
                  `autocomplete` sí es `cc-number` aquí — este campo es de verdad el número, y que el
                  navegador lo rellene evita que se teclee mal. */}
              <TextField
                label={t('card.number')}
                error={messageOf(errors.cardNumber ?? errors.card?.last4 ?? errors.card?.brand)}
                hint={
                  typedBrand === undefined
                    ? t('card.numberHint')
                    : t('card.detected', { brand: t(`common:card.brands.${typedBrand}`) })
                }
                type="text"
                inputMode="numeric"
                autoComplete="cc-number"
                maxLength={NUMBER_MAX_LENGTH}
                placeholder={t('card.numberPlaceholder')}
                {...register('cardNumber')}
              />
            </Col>

            <Col md={5}>
              {/* Mes y año son un solo dato con dos partes, y se rotulan como tal: dos etiquetas
                  sueltas partían el rótulo en dos líneas y descuadraban los controles entre sí. */}
              <Form.Group className="mb-0" role="group" aria-labelledby={expiryLabelId}>
                <span id={expiryLabelId} className="form-label d-block mb-1">
                  {t('card.expiry')}
                </span>
                <div className="d-flex align-items-start gap-2">
                  <SelectField
                    label={t('card.expMonth')}
                    labelVisibility="hidden"
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

                  <span className="pt-2" aria-hidden="true">
                    /
                  </span>

                  <SelectField
                    label={t('card.expYear')}
                    labelVisibility="hidden"
                    error={messageOf(errors.card?.expYear)}
                    autoComplete="cc-exp-year"
                    {...register('card.expYear', {
                      setValueAs: (value: string) => (value === '' ? Number.NaN : Number(value)),
                    })}
                  >
                    {years.map((year) => (
                      <option key={year} value={year}>
                        {year}
                      </option>
                    ))}
                  </SelectField>
                </div>
              </Form.Group>
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
