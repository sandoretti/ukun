import { useTranslation } from 'react-i18next';

import { Section } from '../../../shared/ui/Section';
import { SectionHeading } from '../../../shared/ui/SectionHeading';

/**
 * How Ukun got here, in four milestones. It is an `<ol>` because the order is the content: read out
 * of sequence the block says nothing, and that is precisely what distinguishes an ordered list from
 * a stack of boxes that happen to be printed one under the other.
 *
 * The years are data and the prose is translation: a milestone is added by putting its key here and
 * its three strings in both JSON files, never by pasting a fifth copy of the markup.
 */
const MILESTONES = ['workshop', 'firstStore', 'online', 'today'] as const;

export function BrandTimeline() {
  const { t } = useTranslation('home');

  return (
    <Section spacing="spacious">
      <SectionHeading title={t('timeline.title')} lead={t('timeline.lead')} spacing="spacious" />

      <ol className="timeline__list list-unstyled mb-0">
        {MILESTONES.map((milestone) => (
          <li key={milestone} className="timeline__item">
            <p className="timeline__year mb-1">{t(`timeline.milestones.${milestone}.year`)}</p>
            <h3 className="h5 mb-2">{t(`timeline.milestones.${milestone}.title`)}</h3>
            <p className="mb-0">{t(`timeline.milestones.${milestone}.body`)}</p>
          </li>
        ))}
      </ol>
    </Section>
  );
}
