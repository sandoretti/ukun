import { useTranslation } from 'react-i18next';
import { useFormContext } from 'react-hook-form';
import { Col, Row } from 'react-bootstrap';

import { useFieldMessages } from '../hooks/useFieldMessages';
import { Fieldset } from '../ui/Fieldset';
import { TextField } from '../ui/TextField';
import { ADDRESS_MAX, type AddressFormValues } from './customerFields';

/**
 * Where the orders go: the four fields of the contract's `Address`, in one group.
 *
 * It lives in `shared/` because two screens ask for the very same address — the profile edits it
 * (RF-05) and the signup collects it (RF-06) — and features never import each other. Its strings are
 * in `common` for the same reason.
 *
 * It reads the form from context rather than taking a dozen props: whoever drops it in has already
 * wrapped the form in a `FormProvider`, and the fields it registers are the paths the API itself uses
 * in `errors[].field`, so a server rejection lands on the same control as a client one.
 */
export function AddressFieldset() {
  const { t } = useTranslation('common');
  const messageOf = useFieldMessages('common');
  const {
    register,
    formState: { errors },
  } = useFormContext<AddressFormValues>();

  return (
    <Fieldset legend={t('address.legend')} description={t('address.description')} className="settings-section">
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
  );
}
