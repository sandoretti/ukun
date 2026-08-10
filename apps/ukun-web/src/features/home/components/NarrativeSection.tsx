import type { ReactNode } from 'react';
import { Col, Row } from 'react-bootstrap';

import { Illustration } from '../../../shared/ui/Illustration';
import { Section } from '../../../shared/ui/Section';

type NarrativeSectionProps = {
  title: string;
  /**
   * The body copy, rendered as it arrives. `children` rather than a `body` string, and unwrapped
   * rather than pushed into a `<p>` of the block's choosing: the section owns the layout, the caller
   * owns the prose — which is what lets a block run to two paragraphs, or carry a link.
   */
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
 *
 * Side and background travel together: consecutive blocks alternate both, and that alternation is
 * what tells one from the next without the coloured rule the legacy page drew between them.
 */
export function NarrativeSection({ title, children, illustration, layout }: NarrativeSectionProps) {
  const mediaFirst = layout === 'media-start';

  return (
    <Section tone={mediaFirst ? 'muted' : 'default'}>
      <Row className="align-items-center g-4 g-lg-5">
        <Col md={7} className={mediaFirst ? 'order-md-2' : undefined}>
          <h2 className="h3 mb-3">{title}</h2>
          <div className="narrative__body">{children}</div>
        </Col>
        <Col md={5} className={mediaFirst ? 'order-md-1' : undefined}>
          <Illustration src={illustration} className="mx-auto" />
        </Col>
      </Row>
    </Section>
  );
}
