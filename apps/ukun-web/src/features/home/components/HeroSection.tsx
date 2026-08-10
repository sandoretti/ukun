import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';

import { ProductSearchBox } from '../../../shared/ui/ProductSearchBox';
import { Section } from '../../../shared/ui/Section';

type HeroSectionProps = {
  /** Fragment the secondary call to action jumps to, so the hero does not hardcode the page order. */
  exploreTargetId: string;
};

/**
 * The first thing anyone sees. The legacy home put its text straight on top of the photo at 30 %
 * opacity; that is unreadable and impossible to guarantee, because the contrast of text over a photo
 * depends on the pixel behind it. Here the photo stays a full-bleed band and the words sit on an
 * opaque panel over it, so the contrast is the panel's and does not depend on the image at all.
 *
 * The search box is the same component as the navigation bar's, in its `lg` variant: on this screen
 * looking for something *is* the main action, not a corner of the chrome.
 *
 * The brand is set in type here rather than dropped in as the logotype. The logo file is a bitmap
 * with 13 % of transparent air on each side, so aligning it with the line underneath meant undoing
 * that padding by hand; as type it shares the text's own left edge for free, scales with the
 * viewport, and costs no image request or layout shift. The logotype itself lives in the bar above.
 */
export function HeroSection({ exploreTargetId }: HeroSectionProps) {
  const { t } = useTranslation('home');

  return (
    // La foto y el aire extra a partir de `md` los pone `.hero`; el resto de la banda es el mismo
    // andamiaje que las demás secciones de la página.
    <Section className="hero">
      <div className="hero__panel">
        <p className="hero__eyebrow">{t('hero.tagline')}</p>
        <h1 className="hero__title">{t('hero.title')}</h1>
        <p className="lead mb-4">{t('hero.lead')}</p>

        <ProductSearchBox size="lg" className="mb-4" />

        <div className="d-flex flex-wrap gap-3">
          {/* Navegar es cosa de un enlace, nunca de un `button` con `onClick` (docs/07
              §Accesibilidad): las clases de Bootstrap le dan el aspecto de botón sin quitarle el
              menú contextual ni el «abrir en otra pestaña». */}
          <Link to="/products" className="btn btn-primary btn-lg">
            {t('hero.browse')}
          </Link>
          <a href={`#${exploreTargetId}`} className="btn btn-outline-primary btn-lg">
            {t('hero.explore')}
          </a>
        </div>
      </div>
    </Section>
  );
}
