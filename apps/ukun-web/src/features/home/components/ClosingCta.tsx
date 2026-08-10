import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';

import { Section } from '../../../shared/ui/Section';

/**
 * The last block of the page: the legacy's "explore our collection", turned into the call to action
 * it always wanted to be.
 *
 * The line about the physical stores is text and not a link on purpose — the stores screen is issue
 * #20 and is not merged; the link goes in the day the route exists, and until then a dead end would
 * be worse than a sentence.
 */
export function ClosingCta() {
  const { t } = useTranslation('home');

  return (
    <Section tone="brand" spacing="spacious" className="closing-cta" containerClassName="text-center">
      <h2 className="h2 mb-3">{t('closing.title')}</h2>
      <p className="lead closing-cta__lead mx-auto mb-4">{t('closing.lead')}</p>
      <Link to="/products" className="btn btn-light btn-lg">
        {t('closing.action')}
      </Link>
      <p className="closing-cta__note mb-0 mt-4 small">{t('closing.stores')}</p>
    </Section>
  );
}
