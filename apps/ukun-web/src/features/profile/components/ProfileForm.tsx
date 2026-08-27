import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { FormProvider, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Col, Row } from 'react-bootstrap';

import { SUPPORTED_LANGUAGES } from '../../../i18n/languages';
import { AddressFieldset } from '../../../shared/forms/AddressFieldset';
import { CardFieldset } from '../../../shared/forms/CardFieldset';
import { FavoriteStoreField } from '../../../shared/forms/FavoriteStoreField';
import { applyServerErrors } from '../../../shared/forms/applyServerErrors';
import { NAME_MAX } from '../../../shared/forms/customerFields';
import { HookForm } from '../../../shared/forms/HookForm';
import { useFieldMessages } from '../../../shared/hooks/useFieldMessages';
import { Fieldset } from '../../../shared/ui/Fieldset';
import { ReadOnlyField } from '../../../shared/ui/ReadOnlyField';
import { SelectField } from '../../../shared/ui/SelectField';
import { TextField } from '../../../shared/ui/TextField';
import { useUpdateMyProfile } from '../api/queries';
import type { CustomerProfile } from '../../../shared/api/profile';
import {
  FIELD_MESSAGE_KEYS,
  profileSchema,
  toFormValues,
  toRequest,
  type ProfileFormValues,
} from '../profileSchema';
import { ProfileFormActions, type Outcome } from './ProfileFormActions';

type ProfileFormProps = {
  profile: CustomerProfile;
};

/**
 * The whole of RF-05 as a settings screen: name, delivery address, payment method, favourite store
 * and language, in four groups separated by nothing but a rule and some air.
 *
 * **What it does not ask for is as deliberate as what it does.** There is no field for the card
 * number or the CVV — the contract has nowhere to put them, because storing them is the PCI-DSS
 * violation of the legacy schema — and the email and the password are not edited here either: they
 * are credentials owned by `ukun-auth`, which is also what closes the legacy bug where saving a
 * profile without touching the email could fail with "email already exists" (RF-06, *do not carry
 * over*). The email is shown as what it is, a value, and not as an input that refuses every keystroke.
 *
 * The contract's `PUT` is a replacement and not a patch, so the form always sends every field —
 * including `favoriteStoreId: null`, which is how a favourite store is cleared.
 *
 * It hands the form down through `FormProvider` because the card block needs five different things
 * from it (`register`, `errors`, `control`, `setFocus`, `trigger`); five props is react-hook-form
 * telling you it already brought a context for this.
 */
export function ProfileForm({ profile }: ProfileFormProps) {
  const { t } = useTranslation('profile');
  const messageOf = useFieldMessages('profile');
  const update = useUpdateMyProfile();
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [formError, setFormError] = useState<unknown>(null);

  const form = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    // `values` and not `defaultValues`: the form follows the cache entry, so what the save writes
    // back becomes the new baseline and the fields stop being dirty without a manual `reset`.
    values: toFormValues(profile),
  });
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isDirty },
  } = form;

  const onSubmit = handleSubmit(async (values) => {
    // Read before the save: the answer is a `200` either way, so whether anything had changed is
    // something only this side knows (RF-05, "si no se modifica ningún campo, se informa sin
    // persistir"). It is reported, never dressed up as a save.
    const hadChanges = isDirty;
    setOutcome(null);
    setFormError(null);

    try {
      // `toRequest` is where a typed card number stops: what goes on the wire is the contract's own
      // request, which has nowhere to carry it.
      await update.mutateAsync(toRequest(values));
      setOutcome(hadChanges ? 'saved' : 'unchanged');
    } catch (error) {
      if (!applyServerErrors(error, setError, FIELD_MESSAGE_KEYS)) {
        setFormError(error);
      }
    }
  });

  function discard() {
    // Back to what the server has, which is what `values` holds — and the last verdict goes with it:
    // "changes saved" under a form that no longer holds those changes is a lie.
    reset();
    setOutcome(null);
    setFormError(null);
  }

  return (
    <FormProvider {...form}>
      <HookForm className="settings-form" onSubmit={onSubmit}>
        <Fieldset
          legend={t('personal.legend')}
          description={t('personal.description')}
          className="settings-section"
        >
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
          <ReadOnlyField label={t('personal.email')} hint={t('personal.emailHint')}>
            {profile.email}
          </ReadOnlyField>
        </Fieldset>

        <AddressFieldset />

        <CardFieldset variant="existing" />

        <Fieldset
          legend={t('preferences.legend')}
          description={t('preferences.description')}
          className="settings-section"
        >
          <Row>
            <Col md={6}>
              <FavoriteStoreField />
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

        <ProfileFormActions
          outcome={outcome}
          formError={formError}
          isDirty={isDirty}
          isSaving={update.isPending}
          onDiscard={discard}
        />
      </HookForm>
    </FormProvider>
  );
}
