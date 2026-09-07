import { useTranslation } from 'react-i18next';
import { Col, Row } from 'react-bootstrap';

import { formatNumber } from '../../../shared/format/number';
import { useLocale } from '../../../shared/hooks/useLocale';
import { Section } from '../../../shared/ui/Section';

/**
 * The four figures of the brand, and the only place on the page where a number is the message.
 *
 * Each one is a `value` here and a caption in the JSON: the digits are not translated, the words
 * around them are, and separating the two is what keeps `1200` from being written `1.200` in one
 * language file and `1,200` in the other. Grouping comes from `Intl` at render time.
 *
 * They are a `<dl>` and not a row of `<div>`s: figure and caption are a term and its definition, and
 * the pairing has to survive being read out loud without the layout to hold it together.
 *
 * The tone is `muted` and not `soft`, which is what it wore on the landing page. There it was
 * surrounded by white bands and the tint was what set it apart; here it comes straight after an
 * intro that is already `soft`, and two tinted bands touching read as one — the line between the
 * headline and the figures disappeared. The alternation is what tells one band from the next.
 */
const STATS = [
  { key: 'years', value: 12 },
  { key: 'stores', value: 8 },
  { key: 'products', value: 1200 },
  { key: 'orders', value: 45000 },
] as const;

export function StatBand() {
  const { t } = useTranslation('about');
  const locale = useLocale();

  return (
    <Section tone="muted">
      <Row as="dl" xs={2} lg={4} className="g-4 mb-0 text-center">
        {STATS.map((stat) => (
          <Col key={stat.key}>
            <dt className="stat-band__value text-tabular-nums">
              {t(`stats.${stat.key}.value`, { value: formatNumber(stat.value, locale) })}
            </dt>
            <dd className="stat-band__label mb-0">{t(`stats.${stat.key}.label`)}</dd>
          </Col>
        ))}
      </Row>
    </Section>
  );
}
