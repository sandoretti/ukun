import { useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

type ProductImageProps = {
  /** Composed by the server (RF-10) and optional: there is no id-to-path convention to rebuild here. */
  imageUrl: string | null;
  /**
   * Alternative text. Pass `''` when the surrounding markup already names the product — a thumbnail
   * next to the name it belongs to is decoration, and repeating the name makes a screen reader say
   * it twice.
   */
  alt: string;
  /** The frame. **Both branches wear it**, which is what keeps the box the same size either way. */
  className?: string;
  /**
   * What the empty frame shows. The default is the words, which is the right answer wherever they
   * fit; a thumbnail two lines high has to pass an icon instead.
   */
  fallback?: ReactNode;
};

/**
 * A product photo that degrades to an empty frame of the same size.
 *
 * **Two ways of having no photo, and they look alike on purpose**: no URL at all — the photo is
 * optional (RF-10) — and a URL that fails to load, which is a property of this render and not of the
 * product. Either way what appears is a frame of the same dimensions, never a broken image and never
 * a hole that resizes the layout around it.
 *
 * It lives in `shared/ui/` and not in `features/catalog/` because the catalogue is no longer its only
 * caller: the cart line shows the same photo (issue #120), and features do not import each other
 * (`docs/07-frontend-ux-ui.md`). Its label moved to the `common` namespace along with it, so there is
 * still exactly one translation of "no photo" (ADR-0019).
 *
 * It is deliberately **not** `ProductPhoto`, which stays in the catalogue: that one is the detail
 * sheet's, with a lens, a full-screen viewer and a square frame that no thumbnail wants. What is
 * shared is this — the frame and its two states.
 */
export function ProductImage({ imageUrl, alt, className = '', fallback }: ProductImageProps) {
  const { t } = useTranslation('common');
  const [failed, setFailed] = useState(false);

  if (imageUrl === null || failed) {
    return (
      <div
        className={`${className} d-flex align-items-center justify-content-center text-body-secondary small`}
      >
        {fallback ?? t('product.noImage')}
      </div>
    );
  }

  return <img className={className} src={imageUrl} alt={alt} onError={() => setFailed(true)} />;
}
