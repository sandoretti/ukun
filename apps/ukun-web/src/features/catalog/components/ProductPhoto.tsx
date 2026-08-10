import { useState, type CSSProperties, type PointerEvent } from 'react';
import { useTranslation } from 'react-i18next';

import { Icon } from '../../../shared/ui/Icon';
import { ProductViewer } from './ProductViewer';

type ProductPhotoProps = {
  /** Already composed by the server (RF-10) and optional: there is no id-to-path convention here. */
  imageUrl: string | null;
  /** The photo of a product is described by the product: its name is the alternative text. */
  name: string;
};

/** Where the pointer is over the photo, in percent of its box — what the lens and the panel follow. */
type LensPosition = { x: number; y: number };

/**
 * Follows the mouse over an element and reports where it is, as a percentage of the element's own
 * box. Kept apart from the markup because all of it is edge cases: a finger is not a hover, and a
 * box of zeros —jsdom has no layout— would divide into `NaN` and park the lens in a corner.
 */
function useLens() {
  const [lens, setLens] = useState<LensPosition | null>(null);

  const onPointerMove = (event: PointerEvent<HTMLElement>) => {
    if (event.pointerType !== 'mouse') {
      return;
    }
    const bounds = event.currentTarget.getBoundingClientRect();
    if (bounds.width === 0 || bounds.height === 0) {
      return;
    }
    setLens({
      x: ((event.clientX - bounds.left) / bounds.width) * 100,
      y: ((event.clientY - bounds.top) / bounds.height) * 100,
    });
  };

  return { lens, handlers: { onPointerMove, onPointerLeave: () => setLens(null) } };
}

/**
 * The photo of the detail sheet. Unlike the card of the listing, it is **not** cropped: the frame
 * keeps a square footprint so the page does not jump from one product to the next, and the image sits
 * inside it whole. Most of the catalogue is square, but the beds and the floor lamps are portrait and
 * a `cover` crop cuts their heads off.
 *
 * Two ways of enlarging it, and they are not alternatives to each other: a lens that follows the
 * mouse (there is no such thing on a touch screen or with a keyboard), and a full-screen viewer one
 * click — or one `Enter` — away, which is the one that always exists.
 *
 * It handles the same two ways of having no photo the card does: no URL at all, and a URL that fails
 * to load. Neither of them offers zoom, because there is nothing to enlarge.
 */
export function ProductPhoto({ imageUrl, name }: ProductPhotoProps) {
  const { t } = useTranslation('catalog');
  const [failed, setFailed] = useState(false);
  const [viewerOpen, setViewerOpen] = useState(false);
  const { lens, handlers: lensHandlers } = useLens();

  if (imageUrl === null || failed) {
    return (
      <div className="product-photo product-photo--empty d-flex align-items-center justify-content-center text-body-secondary">
        {t('product.noImage')}
      </div>
    );
  }

  return (
    <div
      className="product-zoom"
      style={
        {
          // The magnified panel paints the same file as a background, so it needs the URL as a value.
          // Quotes inside it would close the `url()` early, hence the escape.
          '--photo': `url("${imageUrl.replaceAll('"', '%22')}")`,
          '--lens-x': `${lens?.x ?? 50}%`,
          '--lens-y': `${lens?.y ?? 50}%`,
        } as CSSProperties
      }
    >
      {/* A button and not a link: it opens a dialog, it does not navigate. */}
      <button
        type="button"
        className="product-zoom__trigger"
        aria-label={t('detail.zoom', { name })}
        onClick={() => setViewerOpen(true)}
        {...lensHandlers}
      >
        <img className="product-photo" src={imageUrl} alt={name} onError={() => setFailed(true)} />
        {/* Visible sign that the photo does something when clicked; what it does is in the label. */}
        <span className="product-zoom__badge" aria-hidden="true">
          <Icon name="zoom_in" size="sm" />
        </span>
        {lens !== null && <span className="product-zoom__lens" aria-hidden="true" />}
      </button>

      {/* Decoration for whoever is using a mouse on a wide screen: the same photo, magnified around
          the point under the cursor. The theme hides it where there is no hover or no room. */}
      {lens !== null && <div className="product-zoom__panel" aria-hidden="true" />}

      <ProductViewer
        show={viewerOpen}
        onHide={() => setViewerOpen(false)}
        imageUrl={imageUrl}
        name={name}
      />
    </div>
  );
}
