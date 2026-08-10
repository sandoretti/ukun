import { useTranslation } from 'react-i18next';
import { Col, Row } from 'react-bootstrap';

import { Illustration } from '../../../shared/ui/Illustration';
import { Section } from '../../../shared/ui/Section';
import { SectionHeading } from '../../../shared/ui/SectionHeading';
import fabricIllustration from '../../../assets/illustrations/fabric.svg';
import packageIllustration from '../../../assets/illustrations/package.svg';
import treeIllustration from '../../../assets/illustrations/tree.svg';

/**
 * What the furniture is made of and how it gets to the door: three columns, each one a heading and a
 * paragraph under a line drawing of what that column is actually about — the tree for where the wood
 * comes from, the cushion for the upholstery, the box for the packaging.
 */
const TOPICS = [
  { key: 'wood', illustration: treeIllustration },
  { key: 'upholstery', illustration: fabricIllustration },
  { key: 'packaging', illustration: packageIllustration },
] as const;

export function MaterialsSection() {
  const { t } = useTranslation('home');

  return (
    <Section tone="muted" spacing="spacious">
      <SectionHeading title={t('materials.title')} lead={t('materials.lead')} spacing="spacious" />

      <Row as="ul" md={3} className="g-4 g-lg-5 list-unstyled mb-0">
        {TOPICS.map((topic) => (
          <Col as="li" key={topic.key}>
            <Illustration src={topic.illustration} size="sm" className="mb-3" />
            <h3 className="h5 mb-2">{t(`materials.topics.${topic.key}.title`)}</h3>
            <p className="mb-0">{t(`materials.topics.${topic.key}.body`)}</p>
          </Col>
        ))}
      </Row>
    </Section>
  );
}
