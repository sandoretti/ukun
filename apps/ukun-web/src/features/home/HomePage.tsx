// Un dibujo por bloque, y cada uno sobre lo que el bloque cuenta: el mueble para el diseño, la
// etiqueta para el precio, las personas para quien atiende. Nada de reaprovechar el que quedaba
// libre — un dibujo que no viene a cuento es ruido con la forma de una ilustración.
import sofaIllustration from '../../assets/illustrations/sofa.svg';
import priceTagIllustration from '../../assets/illustrations/price-tag.svg';
import peopleIllustration from '../../assets/illustrations/people.svg';
import { BrandTimeline } from './components/BrandTimeline';
import { ClosingCta } from './components/ClosingCta';
import { FaqSection } from './components/FaqSection';
import { FeaturedCategories } from './components/FeaturedCategories';
import { HeroSection } from './components/HeroSection';
import { MaterialsSection } from './components/MaterialsSection';
import { StatBand } from './components/StatBand';
import { StoryBlock } from './components/StoryBlock';

/** Where the hero's secondary call to action lands. One constant, so the two ends cannot drift. */
const CATEGORIES_ANCHOR = 'categories';

/**
 * The public landing page, served at `/`. It reads as the legacy home did — a cover, then a run of
 * alternating story blocks — with the parts that were debt taken out: one `<h1>` instead of four,
 * no inline styles, no fixed pixel sizes, and every colour coming from the theme.
 *
 * The long blocks (figures, timeline, materials, questions) are interleaved with the narrative ones
 * rather than stacked at the end: four dense sections in a row is where a reader stops. That order
 * is the whole content of this file, which is why it is a flat list of blocks and not a loop over an
 * array — the sequence is the decision, and it has to be readable at a glance.
 *
 * Only one section talks to the API (the categories), and it does so through the same shared query
 * the catalogue uses. This screen imports nothing from `features/catalog`: what the two share went
 * up to `shared/` (`docs/07-frontend-ux-ui.md` §Estructura).
 */
export default function HomePage() {
  return (
    <>
      <HeroSection exploreTargetId={CATEGORIES_ANCHOR} />

      <FeaturedCategories id={CATEGORIES_ANCHOR} />

      <StoryBlock storyKey="design" illustration={sofaIllustration} layout="media-end" />

      <StatBand />

      <StoryBlock storyKey="price" illustration={priceTagIllustration} layout="media-start" />

      <BrandTimeline />

      <StoryBlock storyKey="people" illustration={peopleIllustration} layout="media-end" />

      <MaterialsSection />

      <FaqSection />

      <ClosingCta />
    </>
  );
}
