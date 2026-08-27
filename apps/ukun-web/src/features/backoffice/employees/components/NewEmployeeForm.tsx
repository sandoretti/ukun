import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button, Col, Form, Row } from 'react-bootstrap';

import { toProblem } from '../../../../shared/api/http';
import { applyServerErrors } from '../../../../shared/forms/applyServerErrors';
import { EMAIL_MAX, PASSWORD_MIN } from '../../../../shared/forms/credentialFields';
import { NAME_MAX } from '../../../../shared/forms/customerFields';
import { HookForm } from '../../../../shared/forms/HookForm';
import { useFieldMessages } from '../../../../shared/hooks/useFieldMessages';
import { ErrorState } from '../../../../shared/ui/ErrorState';
import { Fieldset } from '../../../../shared/ui/Fieldset';
import { TextField } from '../../../../shared/ui/TextField';
import { useCreateEmployee, type EmployeeCreationOutcome } from '../api/queries';
import { employeesPath } from '../routes';
import {
  FIELD_MESSAGE_KEYS,
  JOB_TITLE_MAX,
  employeeSchema,
  emptyEmployeeValues,
  toCreateRequest,
  type EmployeeFormValues,
} from '../employeeSchema';

type NewEmployeeFormProps = {
  storeName: string;
  /** What to do when the saga answers `202`. The screen decides; this form only reports. */
  onActivationPending: () => void;
};

/**
 * The alta of RF-11: an account and a profile, asked for in two groups.
 *
 * **Nothing here chooses a store.** The new employee joins the store of the admin filling this in,
 * resolved server-side from their `account_id` (`docs/08-identidad-y-seguridad.md` §3): the store's
 * name is on screen because the admin should know where they are writing, not because the form has
 * anything to say about it.
 *
 * The two answers of the saga are both successes and are not the same news (`docs/08` §3):
 *
 * - `201` — the account is active, so this lands on the listing filtered by the new employee's
 *   email, where their row is the only one on screen. That is the confirmation, without inventing a
 *   toast system for it. It `replace`s, so the back button does not walk into a form that would now
 *   answer `409`.
 * - `202` — the employee exists but the activation was not confirmed, so signing in may not work
 *   yet. That one is said out loud and stays on this screen; the page decides how.
 *
 * A `409 EMAIL_ALREADY_EXISTS` is painted **under the email field**, where the reader can act on it:
 * the email is unique across the whole system and only `ukun-auth` can say so, so it arrives as a
 * `code` of its own rather than as a field error (`docs/12-error-contract.md` §2). It is the same
 * rejection the customer signup paints, from the same closed registry and the same bundle.
 */
export function NewEmployeeForm({ storeName, onActivationPending }: NewEmployeeFormProps) {
  const { t } = useTranslation('backoffice');
  const messageOf = useFieldMessages('backoffice');
  const navigate = useNavigate();
  const create = useCreateEmployee();

  const [formError, setFormError] = useState<unknown>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<EmployeeFormValues>({
    resolver: zodResolver(employeeSchema),
    defaultValues: emptyEmployeeValues(),
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);

    try {
      const outcome: EmployeeCreationOutcome = await create.mutateAsync(toCreateRequest(values));

      if (outcome === 'activationPending') {
        onActivationPending();
        return;
      }

      void navigate(employeesPath({ q: values.email }), { replace: true });
    } catch (error) {
      if (toProblem(error).code === 'EMAIL_ALREADY_EXISTS') {
        setError(
          'email',
          { type: 'server', message: 'errors:EMAIL_ALREADY_EXISTS' },
          { shouldFocus: true },
        );
        return;
      }

      if (!applyServerErrors(error, setError, FIELD_MESSAGE_KEYS)) {
        setFormError(error);
      }
    }
  });

  // `settings-form` y `settings-section` son el ritmo de los formularios largos de datos, que ya
  // pintan el perfil (RF-05) y el alta de cliente (RF-06). Este es el tercero y es el mismo tipo de
  // pantalla, así que se ve igual: la medida del formulario no es la de la columna, y lo que separa
  // un grupo del siguiente es una divisoria y aire, no una caja.
  return (
    <HookForm className="settings-form" onSubmit={onSubmit}>
      <Fieldset
        legend={t('employees.new.account.legend')}
        description={t('employees.new.account.description')}
        className="settings-section"
      >
        <TextField
          label={t('employees.new.account.email')}
          hint={t('employees.new.account.emailHint')}
          error={messageOf(errors.email)}
          type="email"
          autoComplete="email"
          spellCheck={false}
          maxLength={EMAIL_MAX}
          {...register('email')}
        />
        {/* `new-password` y no `current-password`: es lo que hace que el navegador ofrezca generar
            una, en vez de rellenar la de la propia sesión del admin. */}
        <TextField
          label={t('employees.new.account.password')}
          hint={t('employees.new.account.passwordHint', { min: PASSWORD_MIN })}
          error={messageOf(errors.password)}
          type="password"
          autoComplete="new-password"
          spellCheck={false}
          minLength={PASSWORD_MIN}
          {...register('password')}
        />
      </Fieldset>

      <Fieldset
        legend={t('employees.new.job.legend')}
        description={t('employees.new.job.description', { store: storeName })}
        className="settings-section"
      >
        <Row>
          <Col md={6}>
            <TextField
              label={t('employees.new.job.name')}
              error={messageOf(errors.name)}
              type="text"
              autoComplete="given-name"
              spellCheck={false}
              maxLength={NAME_MAX}
              {...register('name')}
            />
          </Col>
          <Col md={6}>
            <TextField
              label={t('employees.new.job.lastName')}
              error={messageOf(errors.lastName)}
              type="text"
              autoComplete="family-name"
              spellCheck={false}
              maxLength={NAME_MAX}
              {...register('lastName')}
            />
          </Col>
        </Row>

        <TextField
          label={t('employees.new.job.jobTitle')}
          hint={t('employees.new.job.jobTitleHint')}
          error={messageOf(errors.jobTitle)}
          type="text"
          autoComplete="organization-title"
          spellCheck={false}
          maxLength={JOB_TITLE_MAX}
          {...register('jobTitle')}
        />

        {/* El único rol que se elige de verdad dentro de un empleado (RF-12), y por eso es una
            casilla y no un selector de roles: `EMPLOYEE` va implícito y no se negocia. */}
        <Form.Group className="mb-3">
          <Form.Check
            id="employee-is-admin"
            type="checkbox"
            label={t('employees.new.job.isAdmin')}
            aria-describedby="employee-is-admin-hint"
            {...register('isAdmin')}
          />
          <Form.Text id="employee-is-admin-hint" className="d-block form-check-hint">
            {t('employees.new.job.isAdminHint')}
          </Form.Text>
        </Form.Group>
      </Fieldset>

      <div className="settings-actions">
        <div aria-live="polite">{formError !== null && <ErrorState error={formError} />}</div>

        <Button type="submit" variant="primary" disabled={create.isPending}>
          {create.isPending ? t('employees.new.submitting') : t('employees.new.submit')}
        </Button>
      </div>
    </HookForm>
  );
}
