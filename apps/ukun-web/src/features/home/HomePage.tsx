import { useTranslation } from 'react-i18next';

import sofaIllustration from '../../assets/illustrations/sofa.svg';
import packageIllustration from '../../assets/illustrations/package.svg';
import peopleIllustration from '../../assets/illustrations/people.svg';
import { ClosingCta } from './components/ClosingCta';
import { FeaturedCategories } from './components/FeaturedCategories';
import { HeroSection } from './components/HeroSection';
import { NarrativeSection } from './components/NarrativeSection';

/** Where the hero's secondary call to action lands. One constant, so the two ends cannot drift. */
const CATEGORIES_ANCHOR = 'categories';

/**
 * The public landing page, served at `/`. It reads as the legacy home did — a cover, then a run of
 * alternating story blocks — with the parts that were debt taken out: one `<h1>` instead of four,
 * no inline styles, no fixed pixel sizes, and every colour coming from the theme.
 *
 * Only one section talks to the API (the categories), and it does so through the same shared query
 * the catalogue uses. This screen imports nothing from `features/catalog`: what the two share went
 * up to `shared/` (`docs/07-frontend-ux-ui.md` §Estructura).
 */
export default function HomePage() {
  const { t } = useTranslation('home');

  return (
    <>
      <HeroSection exploreTargetId={CATEGORIES_ANCHOR} />

      <FeaturedCategories id={CATEGORIES_ANCHOR} />

      <NarrativeSection
        title={t('story.design.title')}
        illustration={sofaIllustration}
        layout="media-end"
      >
        {t('story.design.body')}
      </NarrativeSection>

      <NarrativeSection
        title={t('story.price.title')}
        illustration={packageIllustration}
        layout="media-start"
      >
        {t('story.price.body')}
      </NarrativeSection>

      <NarrativeSection
        title={t('story.people.title')}
        illustration={peopleIllustration}
        layout="media-end"
      >
        {t('story.people.body')}
      </NarrativeSection>

      <ClosingCta />
    </>
  );
}
