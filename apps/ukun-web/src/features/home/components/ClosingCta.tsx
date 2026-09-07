import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';

import { CATALOG_PATH } from '../../../shared/routes';
import { ClosingBand } from '../../../shared/ui/ClosingBand';

/**
 * The last block of the landing page: the legacy's "explore our collection", turned into the call to
 * action it always wanted to be.
 *
 * One way out, and it is the catalogue: someone who lands on a shop is there to look at products.
 * The stores are the line underneath and not a second button — it used to be text because that
 * screen did not exist, and now that it does the reason is a different one: two equal buttons here
 * would split the intent of a landing page that has exactly one.
 *
 * The band itself is `ClosingBand` in `shared/ui/`, shared with the closing of «Sobre Ukun». What is
 * left in this file is the mapping of the `home` namespace onto it, which is the same division of
 * labour `StoryBlock` has with `NarrativeSection`.
 */
export function ClosingCta() {
  const { t } = useTranslation('home');

  return (
    <ClosingBand title={t('closing.title')} lead={t('closing.lead')} note={t('closing.stores')}>
      <Link to={CATALOG_PATH} className="btn btn-light btn-lg">
        {t('closing.action')}
      </Link>
    </ClosingBand>
  );
}
