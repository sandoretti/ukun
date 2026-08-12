import { useTranslation } from 'react-i18next';
import { useFormContext, useWatch } from 'react-hook-form';

import { useStores } from '../api/stores';
import { useFieldMessages } from '../hooks/useFieldMessages';
import { SelectField } from '../ui/SelectField';

/** The shape this field reads out of the form it is dropped into. */
type FavoriteStoreFormValues = {
  favoriteStoreId: number | null;
};

/**
 * The store proposed by default when checking availability. Optional in both screens that ask for it
 * — the profile (RF-05) and the signup (RF-06) — which is why "none" is a real option and not an
 * empty state.
 *
 * The store is an identifier, not a nested object (ADR-0017 §2): the name is resolved from the list of
 * stores, which is closed reference data already cached for the whole session.
 *
 * **It has to be a controlled select.** Its options arrive from a second read, and an uncontrolled
 * `<select>` keeps whatever the DOM had when it first rendered — "None" — even after the store it
 * points at shows up in the list. The form state was right all along; what was wrong was what the
 * reader saw.
 */
export function FavoriteStoreField() {
  const { t } = useTranslation('common');
  const messageOf = useFieldMessages('common');
  const stores = useStores();
  const {
    control,
    register,
    formState: { errors },
  } = useFormContext<FavoriteStoreFormValues>();

  const favoriteStoreId = useWatch({ control, name: 'favoriteStoreId' });

  return (
    <SelectField
      label={t('favoriteStore.label')}
      error={messageOf(errors.favoriteStoreId)}
      hint={stores.isError ? t('favoriteStore.unavailable') : t('favoriteStore.hint')}
      disabled={stores.isError}
      {...register('favoriteStoreId', {
        /**
         * `null` tiene que sobrevivir, y por eso se comprueba aparte de la cadena vacía: react-hook-form
         * pasa por aquí también el valor **inicial** del campo, y `Number(null)` es `0` —un
         * identificador que no existe—. Con un cliente que ya tenía tienda el fallo no se veía, porque
         * el inicial era un número; con uno que no la tiene, el formulario mandaba `0` y el API lo
         * rechazaba con un `400` sobre este mismo campo.
         */
        setValueAs: (value: string | null) => (value === '' || value === null ? null : Number(value)),
      })}
      value={favoriteStoreId ?? ''}
    >
      <option value="">{t('favoriteStore.none')}</option>
      {(stores.data ?? []).map((store) => (
        <option key={store.id} value={store.id}>
          {store.name}
        </option>
      ))}
    </SelectField>
  );
}
