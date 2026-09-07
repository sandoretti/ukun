import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';

import { CATALOG_PATH } from '../../../shared/routes';
import { ClosingBand } from '../../../shared/ui/ClosingBand';

/**
 * The last band of «Sobre Ukun», with **two** ways out where the landing page's closing has one.
 *
 * That is the difference between the two screens, and it is the only difference: whoever reaches the
 * bottom of this page has just read that fabric samples can be taken home and that availability is
 * shown store by store, so the stores are a real destination and not a footnote. Everything else —
 * the heading, the capped lead, the tone, the centring — is `ClosingBand` in `shared/ui/`, which
 * exists because this file was first written as a copy of `ClosingCta`.
 *
 * No `note`: the line the landing page puts under its button is precisely the one about the physical
 * stores, and here that is the button.
 */
export function AboutClosing() {
  const { t } = useTranslation('about');

  return (
    <ClosingBand title={t('closing.title')} lead={t('closing.lead')}>
      <Link to={CATALOG_PATH} className="btn btn-light btn-lg">
        {t('closing.catalog')}
      </Link>
      <Link to="/stores" className="btn btn-outline-light btn-lg">
        {t('closing.stores')}
      </Link>
    </ClosingBand>
  );
}
