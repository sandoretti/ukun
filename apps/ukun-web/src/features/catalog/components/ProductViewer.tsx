import { useId, useRef, useState, type CSSProperties } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Modal } from 'react-bootstrap';

import { formatNumber } from '../../../shared/format/number';
import { useDragScroll } from '../../../shared/hooks/useDragScroll';
import { useLocale } from '../../../shared/hooks/useLocale';
import { Icon } from '../../../shared/ui/Icon';

type ProductViewerProps = {
  show: boolean;
  onHide: () => void;
  imageUrl: string;
  name: string;
};

/** Fixed steps instead of a free factor: three presses cover the useful range and nothing else. */
const ZOOM_LEVELS = [1, 1.5, 2, 3];
const FIT = 0;
const CLICK_LEVEL = 2;

/**
 * The photo at full screen, which is the only way of enlarging it that works everywhere: the lens of
 * the sheet needs a mouse, and there is none on a phone or when browsing with the keyboard.
 *
 * It is a react-bootstrap `Modal` and not a hand-rolled overlay because the focus trap, `Escape`, the
 * `aria-modal` and giving the focus back to the photo are exactly what it already does — rebuilding
 * that by hand is how a lightbox ends up impossible to leave with the keyboard.
 *
 * Panning is native: past ×1 the frame simply overflows and scrolls, so the arrow keys move the image
 * without a handler of our own. Dragging is a shortcut on top of that, never the only way.
 */
export function ProductViewer({ show, onHide, imageUrl, name }: ProductViewerProps) {
  const { t } = useTranslation('catalog');
  const locale = useLocale();
  const titleId = useId();
  const [level, setLevel] = useState(FIT);

  const frameRef = useRef<HTMLDivElement>(null);

  const zoom = ZOOM_LEVELS[level];
  const isZoomed = level > FIT;
  const dragHandlers = useDragScroll(frameRef, isZoomed);

  return (
    <Modal
      show={show}
      onHide={onHide}
      fullscreen
      aria-labelledby={titleId}
      // Reopening a photo that was left at ×3 and scrolled into a corner is not where anyone wants
      // to start, so the level goes back to "fit" once the modal is gone.
      onExited={() => setLevel(FIT)}
    >
      <Modal.Header closeButton closeLabel={t('viewer.close')}>
        <Modal.Title as="h2" id={titleId} className="h6 mb-0">
          {name}
        </Modal.Title>
      </Modal.Header>

      <Modal.Body className="product-viewer">
        {/* El marco es dos cosas distintas según el estado, y antes era una sola que no era ninguna
            de las dos: un `<div tabIndex={0} onClick>` cuyo «pulsa para ampliar» no respondía a
            ninguna tecla (WCAG 2.1.1) y que, ajustado, era una parada de tabulación sin nombre sobre
            un contenido que no se podía desplazar porque cabía entero.

            Ajustada, la foto cabe y no hay nada que desplazar: lo único que ofrece es ampliar, y eso
            es un botón de verdad. Ampliada, desborda y el desplazamiento lo hace el propio marco, que
            entonces sí tiene que ser enfocable para que las flechas lo muevan — con su rol y su
            nombre, porque una parada de tabulación anónima no le dice nada a quien no ve la foto. */}
        <div
          ref={frameRef}
          className={`product-viewer__frame${isZoomed ? ' is-zoomed' : ''}`}
          style={{ '--zoom': zoom } as CSSProperties}
          {...(isZoomed
            ? { role: 'group' as const, 'aria-label': t('viewer.frame', { name }), tabIndex: 0 }
            : {})}
          {...dragHandlers}
        >
          <img className="product-viewer__image" src={imageUrl} alt={name} />

          {!isZoomed && (
            <button
              type="button"
              className="product-viewer__enlarge"
              aria-label={t('viewer.enlarge', { name })}
              onClick={() => setLevel(CLICK_LEVEL)}
            />
          )}
        </div>

        <div className="product-viewer__controls">
          <Button
            variant="light"
            className="rounded-circle border"
            aria-label={t('viewer.zoomOut')}
            disabled={level === FIT}
            onClick={() => setLevel((current) => current - 1)}
          >
            <Icon name="zoom_out" />
          </Button>

          {/* What changed is announced: the level is the only feedback for someone who cannot see
              the photo grow. */}
          <span className="text-tabular-nums" aria-live="polite">
            {t('viewer.level', { level: formatNumber(zoom, locale) })}
          </span>

          <Button
            variant="light"
            className="rounded-circle border"
            aria-label={t('viewer.zoomIn')}
            disabled={level === ZOOM_LEVELS.length - 1}
            onClick={() => setLevel((current) => current + 1)}
          >
            <Icon name="zoom_in" />
          </Button>
        </div>

        {isZoomed && <p className="small text-body-secondary text-center mb-0">{t('viewer.hint')}</p>}
      </Modal.Body>
    </Modal>
  );
}
