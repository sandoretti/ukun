import type { ReactNode } from 'react';
import { Col, Container, Row } from 'react-bootstrap';

type NarrativeSectionProps = {
  title: string;
  /** The body copy. `children` rather than a `body` prop, so a block can carry a link later on. */
  children: ReactNode;
  illustration: string;
  /**
   * Which side the drawing goes on. A named variant instead of a `reversed` boolean: the two are
   * mirror layouts, not a layout and its negation (`docs/07-frontend-ux-ui.md`).
   */
  layout: 'media-end' | 'media-start';
};

/**
 * One of the alternating story blocks the legacy home had. What it keeps from it is the shape —
 * copy on one side, a line drawing on the other — and not the markup: there is one `<h1>` per page
 * and these are `<h2>`s, the drawing scales with the column instead of being pinned at 250 px, and
 * the separator is the section's own spacing rather than an `<hr>` painted in the brand colour.
 */
export function NarrativeSection({ title, children, illustration, layout }: NarrativeSectionProps) {
  return (
    <section className={`narrative narrative--${layout}`}>
      <Container>
        <Row className="align-items-center g-4 g-lg-5">
          <Col md={7} className={layout === 'media-start' ? 'order-md-2' : undefined}>
            <h2 className="h3 mb-3">{title}</h2>
            <p className="lead mb-0">{children}</p>
          </Col>
          <Col md={5} className={layout === 'media-start' ? 'order-md-1' : undefined}>
            {/* Ilustración de marca, no iconografía de UI: no pasa por `<Icon>` ni entra en el mapa
                de iconos de docs/07. Decorativa, porque lo que dice ya lo dice el texto de al lado. */}
            <img src={illustration} alt="" className="narrative__illustration" />
          </Col>
        </Row>
      </Container>
    </section>
  );
}
