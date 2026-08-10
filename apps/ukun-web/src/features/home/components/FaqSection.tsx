import { useTranslation } from 'react-i18next';
import { Accordion } from 'react-bootstrap';

import { Section } from '../../../shared/ui/Section';
import { SectionHeading } from '../../../shared/ui/SectionHeading';

/**
 * The six questions that get asked before anyone buys furniture online. They close the page's copy:
 * the sections above tell the story, this one answers what the story leaves open.
 *
 * `<Accordion>` from react-bootstrap rather than a hand-rolled one — the button/region pairing,
 * `aria-expanded` and `aria-controls` come with it, and re-implementing them is how a disclosure
 * ends up unusable with a keyboard. It is deliberately not `alwaysOpen`: one answer at a time keeps
 * the block short enough that the closing call to action stays within reach.
 */
const QUESTIONS = ['delivery', 'assembly', 'returns', 'warranty', 'stores', 'payment'] as const;

export function FaqSection() {
  const { t } = useTranslation('home');

  return (
    // La medida se acota: una línea de texto larga a todo el ancho de la página se lee mal.
    <Section spacing="spacious" className="faq" containerClassName="faq__container">
      <SectionHeading title={t('faq.title')} lead={t('faq.lead')} />

      <Accordion flush>
        {QUESTIONS.map((question, index) => (
          <Accordion.Item key={question} eventKey={question}>
            {/* El encabezado del acordeón es un `<h2>` por defecto, y aquí ya hay uno arriba: con
                `as="h3"` la pantalla conserva un solo nivel por escalón. */}
            <Accordion.Header as="h3">
              <span className="faq__number" aria-hidden="true">
                {index + 1}
              </span>
              {t(`faq.items.${question}.question`)}
            </Accordion.Header>
            <Accordion.Body>{t(`faq.items.${question}.answer`)}</Accordion.Body>
          </Accordion.Item>
        ))}
      </Accordion>
    </Section>
  );
}
