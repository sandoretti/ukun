import { useTranslation } from 'react-i18next';

// El dibujo de las personas, que es el del bloque que habla de quien atiende en tienda. Venía de la
// home junto con el bloque; el mueble y la etiqueta de precio se quedaron allí, con los suyos.
import peopleIllustration from '../../assets/illustrations/people.svg';
import { NarrativeSection } from '../../shared/ui/NarrativeSection';
import { AboutClosing } from './components/AboutClosing';
import { AboutIntro } from './components/AboutIntro';
import { BrandTimeline } from './components/BrandTimeline';
import { Commitments } from './components/Commitments';
import { FaqSection } from './components/FaqSection';
import { MaterialsSection } from './components/MaterialsSection';
import { StatBand } from './components/StatBand';

/**
 * «Sobre Ukun», served at `/about`. Until this screen existed it was an entry of the navigation bar
 * rendered as a disabled button, which is what `ComingSoon` was for — and with this route in place
 * that component has no callers left and goes.
 *
 * **Most of what is here is not new copy: it moved.** The landing page carried the whole brand
 * story — nine bands, of which four were long-form reading — so anyone arriving to buy a table
 * crossed twelve years of workshop history before reaching the footer. The split is the point of
 * this screen: `/` keeps what sells (cover, categories, two story blocks, the call to action) and
 * this page takes what is read (the figures, the timeline, the materials, the people, the
 * questions). Nothing is duplicated between the two, which is why the keys moved out of the `home`
 * namespace instead of being copied into `about`.
 *
 * What *is* new is `Commitments`, and it is the reason the page is worth having rather than being a
 * tidier home: the rest says where the brand comes from, that block says what it owes you
 * afterwards.
 *
 * The order interleaves the dense bands with the narrative ones for the same reason the landing page
 * does, and it is written as a flat list of blocks and not a loop over an array because the sequence
 * *is* the content of this file. Nothing here talks to the API: this screen has no data state, so it
 * has no loading, empty or error state either.
 */
export default function AboutPage() {
  const { t } = useTranslation('about');

  return (
    <>
      <AboutIntro />

      <StatBand />

      <BrandTimeline />

      <MaterialsSection />

      {/* Sin `StoryBlock`: ése es el mapeo del namespace `home` sobre `NarrativeSection` y se queda
          en la portada con sus dos historias. Aquí sólo hay una, así que la pantalla llama al
          componente genérico con sus propias claves, que es para lo que recibe la prosa como
          `children`. */}
      <NarrativeSection
        title={t('people.title')}
        illustration={peopleIllustration}
        layout="media-end"
      >
        <p className="lead">{t('people.body')}</p>
        <p className="mb-0">{t('people.bodySecondary')}</p>
      </NarrativeSection>

      <Commitments />

      <FaqSection />

      <AboutClosing />
    </>
  );
}
