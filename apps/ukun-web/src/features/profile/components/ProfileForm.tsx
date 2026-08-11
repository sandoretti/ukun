import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Button, Col, Form, Row } from 'react-bootstrap';

import { SUPPORTED_LANGUAGES } from '../../../i18n/languages';
import { useStores } from '../../../shared/api/stores';
import { applyServerErrors } from '../../../shared/forms/applyServerErrors';
import { ErrorState } from '../../../shared/ui/ErrorState';
import { Fieldset } from '../../../shared/ui/Fieldset';
import { Icon } from '../../../shared/ui/Icon';
import { SelectField } from '../../../shared/ui/SelectField';
import { TextField } from '../../../shared/ui/TextField';
import { useUpdateMyProfile } from '../api/queries';
import type { CustomerProfile } from '../api/profileKeys';
import {
  ADDRESS_MAX,
  CARD_BRANDS,
  FIELD_MESSAGE_KEYS,
  MONTH_MAX,
  MONTH_MIN,
  NAME_MAX,
  YEAR_MAX,
  YEAR_MIN,
  profileSchema,
  toFormValues,
  type ProfileFormValues,
} from '../profileSchema';

type ProfileFormProps = {
  profile: CustomerProfile;
};

const MONTHS = Array.from({ length: MONTH_MAX }, (_, index) => index + MONTH_MIN);

/** What the last submit ended in, for the line that tells the reader so. */
type Outcome = 'saved' | 'unchanged';

/**
 * The whole of RF-05 in one form: name, delivery address, masked card, favourite store and language.
 *
 * **What it does not ask for is as deliberate as what it does.** There is no field for the card
 * number or the CVV — storing them is the PCI-DSS violation of the legacy schema — and the email and
 * the password are not edited here either: they are credentials owned by `ukun-auth`, which is also
 * what closes the legacy bug where saving a profile without touching the email could fail with
 * "email already exists" (RF-06, *do not carry over*).
 *
 * The contract's `PUT` is a replacement and not a patch, so the form always sends every field —
 * including `favoriteStoreId: null`, which is how a favourite store is cleared.
 */
export function ProfileForm({ profile }: ProfileFormProps) {
  const { t } = useTranslation('profile');
  const stores = useStores();
  const update = useUpdateMyProfile();
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  /** A failure that belongs to the form as a whole, kept unnarrowed so `ErrorState` translates its `code`. */
  const [formError, setFormError] = useState<unknown>(null);

  const {
    control,
    register,
    handleSubmit,
    setError,
    formState: { errors, isDirty },
  } = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    // `values` and not `defaultValues`: the form follows the cache entry, so what the save writes
    // back becomes the new baseline and the fields stop being dirty without a manual `reset`.
    values: toFormValues(profile),
  });

  /**
   * The one place a field error becomes text. Whatever put it there —the zod schema or the API's
   * `errors[]`— left a translation key behind, never a sentence.
   */
  const messageOf = (error?: { message?: string }) =>
    error?.message === undefined ? undefined : t(error.message);

  /**
   * The favourite store is the one control that has to be **controlled**: its options arrive from a
   * second read, and an uncontrolled `<select>` keeps whatever the DOM had when it first rendered —
   * "Ninguna" — even after the store it points at shows up in the list. The form state was right all
   * along; what was wrong was what the reader saw.
   */
  const favoriteStoreId = useWatch({ control, name: 'favoriteStoreId' });

  const onSubmit = handleSubmit(async (values) => {
    // Read before the save: the answer is a `200` either way, so whether anything had changed is
    // something only this side knows (RF-05, "si no se modifica ningún campo, se informa sin
    // persistir"). It is reported, never dressed up as a save.
    const hadChanges = isDirty;
    setOutcome(null);
    setFormError(null);

    try {
      await update.mutateAsync(values);
      setOutcome(hadChanges ? 'saved' : 'unchanged');
    } catch (error) {
      if (!applyServerErrors(error, setError, FIELD_MESSAGE_KEYS)) {
        setFormError(error);
      }
    }
  });

  return (
    <Form noValidate onSubmit={(event) => void onSubmit(event)}>
      <Fieldset legend={t('personal.legend')}>
        <Row>
          <Col md={6}>
            <TextField
              label={t('personal.name')}
              error={messageOf(errors.name)}
              type="text"
              autoComplete="given-name"
              maxLength={NAME_MAX}
              {...register('name')}
            />
          </Col>
          <Col md={6}>
            <TextField
              label={t('personal.lastName')}
              error={messageOf(errors.lastName)}
              type="text"
              autoComplete="family-name"
              maxLength={NAME_MAX}
              {...register('lastName')}
            />
          </Col>
        </Row>

        {/* El correo se enseña, no se edita: es una proyección del claim del token y la credencial
            vive en ukun-auth (docs/08 §2). No entra en el formulario porque no viaja en el PUT. */}
        <TextField
          label={t('personal.email')}
          hint={t('personal.emailHint')}
          type="email"
          name="email"
          autoComplete="email"
          readOnly
          defaultValue={profile.email}
        />
      </Fieldset>

      <Fieldset legend={t('address.legend')}>
        <TextField
          label={t('address.street')}
          error={messageOf(errors.address?.street)}
          type="text"
          autoComplete="address-line1"
          maxLength={ADDRESS_MAX}
          {...register('address.street')}
        />
        <Row>
          <Col md={5}>
            <TextField
              label={t('address.city')}
              error={messageOf(errors.address?.city)}
              type="text"
              autoComplete="address-level2"
              maxLength={ADDRESS_MAX}
              {...register('address.city')}
            />
          </Col>
          <Col md={4}>
            <TextField
              label={t('address.province')}
              error={messageOf(errors.address?.province)}
              type="text"
              autoComplete="address-level1"
              maxLength={ADDRESS_MAX}
              {...register('address.province')}
            />
          </Col>
          <Col md={3}>
            <TextField
              label={t('address.postalCode')}
              error={messageOf(errors.address?.postalCode)}
              type="text"
              inputMode="numeric"
              autoComplete="postal-code"
              maxLength={5}
              {...register('address.postalCode')}
            />
          </Col>
        </Row>
      </Fieldset>

      <Fieldset legend={t('card.legend')} description={t('card.description')}>
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
      </Fieldset>

      {/* Defecto #19 del legacy, cerrado en el backend con instantáneas inmutables (ADR-0011). Se
          dice en pantalla porque el miedo a "romper" un pedido antiguo es del lector, no del
          esquema. */}
      <Alert variant="light" className="border d-flex gap-2 align-items-start">
        <Icon name="local_shipping" size="sm" />
        <span>{t('ordersNotice')}</span>
      </Alert>

      <Fieldset legend={t('preferences.legend')}>
        <Row>
          <Col md={6}>
            {/* La tienda es un identificador, no un objeto anidado (ADR-0017 §2): el nombre se
                resuelve con la lista de tiendas, que ya está en caché para toda la sesión. */}
            <SelectField
              label={t('preferences.favoriteStore')}
              error={messageOf(errors.favoriteStoreId)}
              hint={stores.isError ? t('preferences.favoriteStoreUnavailable') : t('preferences.favoriteStoreHint')}
              disabled={stores.isError}
              {...register('favoriteStoreId', {
                setValueAs: (value: string) => (value === '' ? null : Number(value)),
              })}
              value={favoriteStoreId ?? ''}
            >
              <option value="">{t('preferences.favoriteStoreNone')}</option>
              {(stores.data ?? []).map((store) => (
                <option key={store.id} value={store.id}>
                  {store.name}
                </option>
              ))}
            </SelectField>
          </Col>
          <Col md={6}>
            <SelectField
              label={t('preferences.locale')}
              error={messageOf(errors.locale)}
              hint={t('preferences.localeHint')}
              {...register('locale')}
            >
              {SUPPORTED_LANGUAGES.map((language) => (
                <option key={language} value={language}>
                  {t(`preferences.locales.${language}`)}
                </option>
              ))}
            </SelectField>
          </Col>
        </Row>
      </Fieldset>

      {/* La región existe siempre, aunque esté vacía: un contenedor que aparece con el mensaje
          dentro no se anuncia (docs/07 §Accesibilidad). */}
      <div aria-live="polite">
        {formError !== null && <ErrorState error={formError} />}
        {/* Guardado y "no había nada que cambiar" no son el mismo desenlace y no se pintan igual:
            solo el primero lleva el icono de confirmación del mapa de `docs/07` §Iconografía. */}
        {outcome !== null && (
          <Alert variant={outcome === 'saved' ? 'success' : 'secondary'} className="d-flex gap-2 align-items-center">
            {outcome === 'saved' && <Icon name="check_circle" size="sm" />}
            <span>{t(outcome)}</span>
          </Alert>
        )}
      </div>

      <Button type="submit" variant="primary" disabled={update.isPending}>
        {update.isPending ? t('submitting') : t('submit')}
      </Button>
    </Form>
  );
}
