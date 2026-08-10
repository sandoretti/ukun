import { useTranslation } from 'react-i18next';

import { NarrativeSection } from './NarrativeSection';

type StoryBlockProps = {
  /** Key under `home:story` — it resolves the heading and the two paragraphs of the block. */
  storyKey: 'design' | 'price' | 'people';
  illustration: string;
  layout: 'media-end' | 'media-start';
};

/**
 * One story of the brand, read whole from the `home` namespace.
 *
 * `NarrativeSection` stays generic — it takes prose as `children` and knows nothing about i18n — and
 * this is the one place that maps a story key onto it. Without it, every block on the page repeats
 * the same three `t(...)` calls and the same two paragraph classes, and the day the second paragraph
 * needs a different treatment there are three copies to find.
 */
export function StoryBlock({ storyKey, illustration, layout }: StoryBlockProps) {
  const { t } = useTranslation('home');

  return (
    <NarrativeSection title={t(`story.${storyKey}.title`)} illustration={illustration} layout={layout}>
      <p className="lead">{t(`story.${storyKey}.body`)}</p>
      <p className="mb-0">{t(`story.${storyKey}.bodySecondary`)}</p>
    </NarrativeSection>
  );
}
