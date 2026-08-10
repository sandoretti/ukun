import { useId } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Form, InputGroup } from 'react-bootstrap';

import { Icon } from '../../../shared/ui/Icon';

type StoreFilterProps = {
  value: string;
  onChange: (value: string) => void;
  /**
   * How many stores are left, announced politely. Empty while the read is in flight — «0 tiendas»
   * before the answer arrives is not a result, it is a wrong one.
   */
  status: string;
};

/**
 * The filter of the stores listing. Not the `SearchBox` of the navigation bar: that one is a combobox
 * with typeahead over the catalogue, and this is a filter over a handful of rows already in memory.
 *
 * It filters as it is typed, with no submit button and no debounce, because there is no request
 * behind it. The label is visible rather than hidden — here the field is a control of the screen,
 * not the site-wide search, and there is nothing else nearby to say what it filters.
 */
export function StoreFilter({ value, onChange, status }: StoreFilterProps) {
  const { t } = useTranslation('stores');
  const inputId = useId();

  return (
    <Form
      role="search"
      className="mb-4"
      // Enter has nothing to submit: the listing is already filtered by the time the key is pressed.
      onSubmit={(event) => event.preventDefault()}
    >
      <Form.Label htmlFor={inputId}>{t('search.label')}</Form.Label>
      <InputGroup>
        <Form.Control
          id={inputId}
          type="search"
          autoComplete="off"
          placeholder={t('search.placeholder')}
          value={value}
          onChange={(event) => onChange(event.target.value)}
        />
        {value !== '' && (
          <Button
            type="button"
            variant="outline-secondary"
            onClick={() => onChange('')}
            aria-label={t('search.clear')}
          >
            <Icon name="close" size="sm" />
          </Button>
        )}
      </InputGroup>
      {/* What changes without the user looking at it has to be announced (docs/07 §Accesibilidad):
          the count moves with every keystroke, and the eyes are on the field. */}
      <p className="mt-2 mb-0 small text-body-secondary" role="status">
        {status}
      </p>
    </Form>
  );
}
