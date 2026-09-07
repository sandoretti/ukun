import { useTranslation } from 'react-i18next';

import { Section } from '../../../shared/ui/Section';

/**
 * The opening of the screen, and the only `<h1>` on it.
 *
 * It is deliberately **not** `HeroSection`. That one is the shop's cover: a full-bleed photograph
 * with the search box and two calls to action into the catalogue, because on the landing page
 * looking for something is the main action. Here the reader has already chosen to read, so the band
 * is a tinted panel of prose and nothing else — no photo to compete with the words, no search field
 * that this screen has no use for.
 *
 * The measure is capped from the theme (`.about-intro`) rather than by a column of the grid: what is
 * being limited is the length of a line of text, which is a typographic decision and not a layout
 * one.
 */
export function AboutIntro() {
  const { t } = useTranslation('about');

  return (
    <Section tone="soft" spacing="spacious" containerClassName="about-intro">
      <h1 className="about-intro__title">{t('intro.title')}</h1>
      <p className="lead">{t('intro.lead')}</p>
      <p className="mb-0">{t('intro.body')}</p>
    </Section>
  );
}
