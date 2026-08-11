import { useTranslation } from 'react-i18next';

import wordmark from '../../assets/ukun-wordmark-light.png';

/**
 * The logotype, as the shell draws it: the white silhouette and never the bicolour original, whose
 * two greens would not stand out against the green of the bar (`docs/07-frontend-ux-ui.md`).
 *
 * It lives on its own because two shells need it — the navigation bar of the site and the slim bar
 * of the sign-in screen — and its three details are easy to get wrong separately: the `alt` names
 * the brand because this is always inside the link home and a link without text has no accessible
 * name; the intrinsic dimensions are attributes so the bar does not jump when the image loads; and
 * the height comes from the theme, not from here.
 */
export function BrandMark() {
  const { t } = useTranslation('common');

  return (
    <img src={wordmark} alt={t('title')} className="navbar-brand__logo" width={882} height={254} />
  );
}
