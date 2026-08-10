import { useState } from 'react';
import { useTranslation } from 'react-i18next';

type ProductPhotoProps = {
  /** Already composed by the server (RF-10) and optional: there is no id-to-path convention here. */
  imageUrl: string | null;
  /** The photo of a product is described by the product: its name is the alternative text. */
  name: string;
};

/**
 * The photo of the detail sheet, with the same two ways of not having one the card handles: no URL
 * at all, and a URL that fails to load. Whether the file answers is a property of this render and
 * not of the product, so a 404 on the image falls back to the same box instead of leaving a broken
 * icon in the middle of the sheet.
 */
export function ProductPhoto({ imageUrl, name }: ProductPhotoProps) {
  const { t } = useTranslation('catalog');
  const [failed, setFailed] = useState(false);

  if (imageUrl === null || failed) {
    return (
      <div className="product-media rounded border d-flex align-items-center justify-content-center text-body-secondary">
        {t('product.noImage')}
      </div>
    );
  }

  return (
    <img
      className="product-media rounded border"
      src={imageUrl}
      alt={name}
      onError={() => setFailed(true)}
    />
  );
}
