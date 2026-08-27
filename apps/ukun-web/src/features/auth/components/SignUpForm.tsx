import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { FormProvider, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button, Col, Row } from 'react-bootstrap';

import { AddressFieldset } from '../../../shared/forms/AddressFieldset';
import { CardFieldset } from '../../../shared/forms/CardFieldset';
import { FavoriteStoreField } from '../../../shared/forms/FavoriteStoreField';
import { applyServerErrors } from '../../../shared/forms/applyServerErrors';
import { NAME_MAX } from '../../../shared/forms/customerFields';
import { toProblem } from '../../../shared/api/http';
import { HookForm } from '../../../shared/forms/HookForm';
import { useFieldMessages } from '../../../shared/hooks/useFieldMessages';
import { ErrorState } from '../../../shared/ui/ErrorState';
import { Fieldset } from '../../../shared/ui/Fieldset';
import { TextField } from '../../../shared/ui/TextField';
import { useSignup, type SignupOutcome } from '../api/queries';
import {
  EMAIL_MAX,
  FIELD_MESSAGE_KEYS,
  PASSWORD_MIN,
  emptySignupValues,
  signupSchema,
  toSignupRequest,
  type SignupFormValues,
} from '../signupSchema';

type SignUpFormProps = {
  /** What to do once the saga answers. The screen decides; this form only reports. */
  onSignedUp: (outcome: SignupOutcome) => void;
};

/**
 * The whole of RF-06 as one form: credentials, name, delivery address, payment method and —optional—
 * a favourite store, in four groups separated by nothing but a rule and some air.
 *
 * **The grouping is the point.** `docs/07-frontend-ux-ui.md` §Accesibilidad names the legacy signup by
 * hand as the defect to fix: one long form with no grouping at all. Four `fieldset`/`legend` are what
 * a screen reader repeats when the reader tabs into each field.
 *
 * **What it does not ask for is as deliberate as what it does.** No CVV — storing it is the PCI-DSS
 * violation of the legacy schema (`docs/08-identidad-y-seguridad.md` §5) — and no language either:
 * `SignupRequest` has no `locale`, so a new customer starts in the language the interface is already
 * speaking and changes it in their profile.
 *
 * The address and the card are the shared fieldsets of `shared/forms/`: the profile edits exactly the
 * same fields, and a second copy of them would be a second chance of drifting from the contract.
 */
export function SignUpForm({ onSignedUp }: SignUpFormProps) {
  const { t } = useTranslation('signup');
  const messageOf = useFieldMessages('signup');
  const signup = useSignup();
  const [formError, setFormError] = useState<unknown>(null);

  const form = useForm<SignupFormValues>({
    resolver: zodResolver(signupSchema),
    defaultValues: emptySignupValues(),
  });
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = form;

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);

    try {
      // `toSignupRequest` is where the typed card number stops: what goes on the wire is the
      // contract's own request, which has nowhere to carry it.
      onSignedUp(await signup.mutateAsync(toSignupRequest(values)));
    } catch (error) {
      // The email is unique across the whole system and only `ukun-auth` can say so, so this
      // rejection arrives as a `code` of its own rather than as a field error (`docs/12` §2). It is
      // painted where the reader can act on it — under the field — and the message comes from the
      // shared `errors` bundle, which already has it in both languages.
      if (toProblem(error).code === 'EMAIL_ALREADY_EXISTS') {
        setError('email', { type: 'server', message: 'errors:EMAIL_ALREADY_EXISTS' }, { shouldFocus: true });
        return;
      }

      if (!applyServerErrors(error, setError, FIELD_MESSAGE_KEYS)) {
        setFormError(error);
      }
    }
  });

  return (
    <FormProvider {...form}>
      <HookForm className="settings-form" onSubmit={onSubmit}>
        <Fieldset legend={t('account.legend')} description={t('account.description')} className="settings-section">
          <TextField
            label={t('account.email')}
            error={messageOf(errors.email)}
            hint={t('account.emailHint')}
            type="email"
            autoComplete="email"
            maxLength={EMAIL_MAX}
            {...register('email')}
          />
          {/* `new-password` y no `current-password`: es lo que hace que el navegador ofrezca generar
              una, en vez de rellenar la de otra cuenta. */}
          <TextField
            label={t('account.password')}
            error={messageOf(errors.password)}
            hint={t('account.passwordHint', { min: PASSWORD_MIN })}
            type="password"
            autoComplete="new-password"
            minLength={PASSWORD_MIN}
            {...register('password')}
          />
        </Fieldset>

        <Fieldset legend={t('personal.legend')} description={t('personal.description')} className="settings-section">
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
        </Fieldset>

        <AddressFieldset />

        <CardFieldset variant="new" />

        <Fieldset
          legend={t('preferences.legend')}
          description={t('preferences.description')}
          className="settings-section"
        >
          <Row>
            <Col md={6}>
              <FavoriteStoreField />
            </Col>
          </Row>
        </Fieldset>

        <div className="settings-actions">
          <div aria-live="polite">{formError !== null && <ErrorState error={formError} />}</div>

          <Button type="submit" variant="primary" size="lg" className="w-100" disabled={signup.isPending}>
            {signup.isPending ? t('submitting') : t('submit')}
          </Button>

          {/* Dónde se teclea la contraseña la segunda vez, dicho antes de crear la cuenta y no
              después: la entrada no es una pantalla de este sitio (ADR-0003). */}
          <p className="form-text mt-3 mb-0">{t('afterSubmitHint')}</p>
        </div>
      </HookForm>
    </FormProvider>
  );
}
