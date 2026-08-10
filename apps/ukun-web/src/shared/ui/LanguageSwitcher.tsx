import { useTranslation } from 'react-i18next';

import { SUPPORTED_LANGUAGES } from '../../i18n/languages';
import { useLocale } from '../hooks/useLocale';

/**
 * The language control of the navigation bar. It replaces a labelled `<select>`: with exactly two
 * options a dropdown asks for three interactions (open, read, choose) to do what two buttons do in
 * one, and it dragged a visible `<label>` across the bar to say something the two captions already
 * say.
 *
 * The active language is `aria-pressed`, not a class — the state has to reach a screen reader, and a
 * highlighted background is not state. Each button reads out the full language name (the visible
 * "ES" is an abbreviation, and an accessible name of two letters is not a name), so the group is
 * announced as "Language: Spanish, pressed / English".
 */
export function LanguageSwitcher() {
  const { t, i18n } = useTranslation('common');
  const active = useLocale();

  return (
    <div className="lang-switch" role="group" aria-label={t('language.label')}>
      {SUPPORTED_LANGUAGES.map((language) => (
        <button
          key={language}
          type="button"
          className="lang-switch__option"
          aria-pressed={language === active}
          onClick={() => void i18n.changeLanguage(language)}
        >
          <span aria-hidden="true">{t(`language.short.${language}`)}</span>
          <span className="visually-hidden">{t(`language.${language}`)}</span>
        </button>
      ))}
    </div>
  );
}
