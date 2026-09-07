// Un dibujo por bloque, y cada uno sobre lo que el bloque cuenta: el mueble para el diseño, la
// etiqueta para el precio. Nada de reaprovechar el que quedaba libre — un dibujo que no viene a
// cuento es ruido con la forma de una ilustración. El de las personas se fue con su bloque a
// «Sobre Ukun».
import sofaIllustration from '../../assets/illustrations/sofa.svg';
import priceTagIllustration from '../../assets/illustrations/price-tag.svg';
import { ClosingCta } from './components/ClosingCta';
import { FeaturedCategories } from './components/FeaturedCategories';
import { HeroSection } from './components/HeroSection';
import { StoryBlock } from './components/StoryBlock';

/** Where the hero's secondary call to action lands. One constant, so the two ends cannot drift. */
const CATEGORIES_ANCHOR = 'categories';

/**
 * The public landing page, served at `/`. It reads as the legacy home did — a cover, then a run of
 * alternating story blocks — with the parts that were debt taken out: one `<h1>` instead of four,
 * no inline styles, no fixed pixel sizes, and every colour coming from the theme.
 *
 * **It used to carry the whole brand story and no longer does.** Nine bands, four of them long-form
 * reading, meant that someone arriving to buy a table crossed twelve years of workshop history
 * before reaching the footer. The figures, the timeline, the materials, the people and the questions
 * moved to `/about` (`features/about/AboutPage`), and with them their translation keys: what is left
 * here is what sells — the cover, the categories, two stories and the call to action. The split is
 * why `home.json` no longer has a `stats` or a `faq` block, rather than those keys having been
 * copied into the new namespace.
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

      {/* Los dos bloques narrativos que se quedan. Alternan lado y fondo, que es lo que distingue
          uno del siguiente sin el filete de color que dibujaba la página legacy. */}
      <StoryBlock storyKey="design" illustration={sofaIllustration} layout="media-end" />

      <StoryBlock storyKey="price" illustration={priceTagIllustration} layout="media-start" />

      <ClosingCta />
    </>
  );
}
