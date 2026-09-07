import { useTranslation } from 'react-i18next';
import { Col, Row } from 'react-bootstrap';

import { Section } from '../../../shared/ui/Section';
import { SectionHeading } from '../../../shared/ui/SectionHeading';

/**
 * The four things a customer can hold Ukun to. It is the block this screen exists for: the timeline
 * says where the brand comes from and the materials say what the furniture is made of, but neither
 * answers what happens after the delivery van leaves.
 *
 * Every one of the four is a claim the system already keeps, not a slogan written to fill the band:
 * the warranty and the spare parts are the ones the FAQ answers, availability store by store is
 * RF-01 — the catalogue really does show a store with none left — and the card one is
 * `docs/08-identidad-y-seguridad.md` §5, where storing the PAN and the CVV is the legacy defect this
 * system was built not to repeat. A promise here that the code did not keep would be the worst kind
 * of copy: the screen next door disproves it.
 *
 * It is the one band of the page on the brand tint, and that is not decoration either: without it
 * the people block, this one and the questions were three white bands in a row, which left the
 * block the screen exists for as the least visible of the three.
 *
 * A `<dl>`, like `StatBand`: each item is a term and what it commits to, and that pairing has to
 * survive being read out loud without the two-column grid holding it together. No illustrations —
 * the six brand drawings are about materials and people, and reaching for whichever one was free
 * would put a picture of a tree next to a paragraph about card numbers.
 */
const COMMITMENTS = ['warranty', 'spares', 'availability', 'payment'] as const;

export function Commitments() {
  const { t } = useTranslation('about');

  return (
    <Section tone="soft" spacing="spacious">
      <SectionHeading title={t('commitments.title')} lead={t('commitments.lead')} spacing="spacious" />

      <Row as="dl" xs={1} md={2} className="g-4 g-lg-5 mb-0">
        {COMMITMENTS.map((commitment) => (
          <Col key={commitment}>
            {/* El término es un `<dt>` y a la vez un escalón del índice de la página, así que lleva
                `<h3>` dentro: sin él la pantalla tendría cuatro títulos que no están en el esquema
                de encabezados y quien la recorra por encabezados se los salta. */}
            <dt className="commitment__term">
              <h3 className="h6 mb-0">{t(`commitments.items.${commitment}.title`)}</h3>
            </dt>
            <dd className="commitment__body mb-0">{t(`commitments.items.${commitment}.body`)}</dd>
          </Col>
        ))}
      </Row>
    </Section>
  );
}
