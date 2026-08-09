import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Container } from 'react-bootstrap';

import { ProductSearchBox } from '../../../shared/ui/ProductSearchBox';
import logo from '../../../assets/ukun-logo.png';

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
 */
export function HeroSection({ exploreTargetId }: HeroSectionProps) {
  const { t } = useTranslation('home');

  return (
    <section className="hero">
      <Container>
        <div className="hero__panel">
          {/* Decorativo: la marca ya la nombran la barra de navegación y el propio encabezado, así
              que repetirla en el texto alternativo solo añade ruido a un lector de pantalla. */}
          <img src={logo} alt="" className="hero__logo" width={1200} height={400} />
          <p className="hero__tagline">{t('hero.tagline')}</p>
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
      </Container>
    </section>
  );
}
