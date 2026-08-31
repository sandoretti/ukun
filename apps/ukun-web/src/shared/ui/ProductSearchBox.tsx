import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { createSearchParams, useNavigate, useSearchParams } from 'react-router-dom';

import { SUGGEST_MIN_LENGTH, useProductSuggestions } from '../api/products';
import { useDebouncedValue } from '../hooks/useDebouncedValue';
import { useLocale } from '../hooks/useLocale';
import { formatMoney } from '../format/money';
import { SearchBox, type SearchSuggestion } from './SearchBox';

type ProductSearchBoxProps = {
  /** See `SearchBox`: `md` in the navigation bar, `lg` when the field leads a screen. */
  size?: 'md' | 'lg';
  className?: string;
};

/**
 * The catalog's search field, the one in the navigation bar. It lives in `shared/` because its
 * consumers are the layout (`app/`) and, later, the landing page — never a feature importing
 * another feature (`docs/07-frontend-ux-ui.md`).
 *
 * Both submitting and picking a suggestion end in the same place: the listing filtered by name.
 * Neither goes to the product detail, which does not exist yet (issue #19); when it does, the only
 * thing that changes is where `onSelect` navigates to.
 */
export function ProductSearchBox({ size = 'md', className }: ProductSearchBoxProps) {
  const { t } = useTranslation('common');
  const locale = useLocale();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const nameParam = searchParams.get('name')?.trim() ?? '';
  const [term, setTerm] = useState(nameParam);
  const [seededFrom, setSeededFrom] = useState(nameParam);

  // The URL is the source of truth for the active filter: if it changes from outside (the back
  // button, removing the chip on the catalog), the field follows. Adjusting the state during render
  // is React's own recipe for this, and it saves an effect plus its extra paint.
  if (nameParam !== seededFrom) {
    setSeededFrom(nameParam);
    setTerm(nameParam);
  }

  const debouncedTerm = useDebouncedValue(term);
  const { data } = useProductSuggestions(debouncedTerm);

  const suggestions = useMemo<SearchSuggestion[]>(
    () =>
      // Guarded by the text on screen, not by the debounced one: emptying the field closes the
      // popup on the spot. While a new list is in flight, `keepPreviousData` keeps the previous one
      // — a list that blinks on every keystroke is unusable.
      term.trim().length < SUGGEST_MIN_LENGTH
        ? []
        : (data ?? []).map((product) => ({
            id: product.id,
            label: product.name,
            meta: formatMoney(product.price, locale),
          })),
    [data, term, locale],
  );

  function search(name: string) {
    const trimmed = name.trim();
    navigate({
      pathname: '/products',
      search: trimmed ? `?${createSearchParams({ name: trimmed })}` : '',
    });
  }

  return (
    <SearchBox
      className={className}
      size={size}
      label={t('search.label')}
      // En la portada este mismo buscador aparece dos veces —el de la barra y el que encabeza la
      // pantalla—, así que el grande se nombra por lo que es allí. Con el mismo nombre los dos, un
      // lector de pantalla lista dos regiones de búsqueda indistinguibles.
      landmarkLabel={size === 'lg' ? t('search.heroLandmark') : undefined}
      submitLabel={t('search.submit')}
      placeholder={t('search.placeholder')}
      value={term}
      onChange={setTerm}
      onSubmit={search}
      suggestions={suggestions}
      onSelect={(suggestion) => {
        setTerm(suggestion.label);
        search(suggestion.label);
      }}
      hint={t('search.hint')}
      status={suggestions.length > 0 ? t('search.status', { count: suggestions.length }) : ''}
    />
  );
}
