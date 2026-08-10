import { useId } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Col, Row } from 'react-bootstrap';

import type { Store } from '../../../shared/api/stores';
import { Icon } from '../../../shared/ui/Icon';
import { StoreAddress } from './StoreAddress';
import { StorePhoneLink } from './StorePhoneLink';

type OnlineStoreBannerProps = {
  store: Store;
};

/**
 * The online store, in a panel of its own instead of a card in the grid.
 *
 * Two reasons, and the second is a bug: it is the only store orders are served from (RF-04), so it
 * does not read as one shop among five; and it is the only one carrying a badge and a note, so
 * inside the grid it was always taller than its neighbours and broke the row.
 *
 * It is told apart by `isOnline` — never by its name, and with no defensive branch for a second one:
 * exactly one row has it, and that is a database invariant (`ux_store_online`), not a convention.
 * It is still an `article` named by its heading, so the screen stays a list of named regions.
 */
export function OnlineStoreBanner({ store }: OnlineStoreBannerProps) {
  const { t } = useTranslation('stores');
  const titleId = useId();

  return (
    <article aria-labelledby={titleId} className="store-online p-4 p-lg-5 mb-5">
      <Row className="g-4 align-items-center">
        <Col lg={7}>
          <div className="d-flex align-items-center gap-3 mb-3">
            {/* El icono del envío: es la tienda desde la que sale la mercancía, no un local. Sale
                del mapa cerrado de iconos de docs/07 — aquí no se elige uno a medida. */}
            <span className="store-online__mark">
              <Icon name="local_shipping" size="lg" />
            </span>
            <div>
              <span className="badge store-online__badge mb-1">{t('online.badge')}</span>
              <h2 id={titleId} className="h4 mb-0">
                {store.name}
              </h2>
            </div>
          </div>

          {/* El distintivo por sí solo se leería como una invitación a visitar un almacén. */}
          <p className="mb-0">{t('online.note')}</p>
        </Col>

        <Col lg={5}>
          {/* La dirección y el teléfono también aquí: RF-02 pide el listado público con los dos, y
              esta tienda es parte del listado, no un anuncio que lo encabeza. */}
          <StoreAddress address={store.address} className="mb-3" />

          <p className="mb-3">
            <StorePhoneLink store={store} className="store-online__link" />
          </p>

          {/* Contra esta tienda se sirven los pedidos, así que lo que sigue a leerla es el catálogo.
              `btn-light` y no `btn-primary`: un botón verde sobre el verde del panel no se vería.
              `Link` con clase de botón, como el cierre de la portada — un `Button` que navega es un
              `<button>` disfrazado de enlace. */}
          <Link to="/products" className="btn btn-light">
            {t('online.cta')}
            <Icon name="arrow_forward" size="sm" className="ms-2" />
          </Link>
        </Col>
      </Row>
    </article>
  );
}
