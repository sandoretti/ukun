import { useId } from 'react';
import { useTranslation } from 'react-i18next';
import { Card } from 'react-bootstrap';

import type { Store } from '../../../shared/api/stores';
import { Icon } from '../../../shared/ui/Icon';
import { directionsUrl } from '../directions';
import { StoreAddress } from './StoreAddress';
import { StorePhoneLink } from './StorePhoneLink';

type StoreCardProps = {
  /** A store you can walk into. The online one has its own panel — see `OnlineStoreBanner`. */
  store: Store;
};

/**
 * One physical store, with the address and the phone number RF-02 asks a public listing to carry,
 * plus the way to get there.
 *
 * Every card in the grid has exactly this shape — there is no branch that adds a line to some of
 * them — which is what keeps a row of cards level. What varies is only the address, and the theme
 * reserves it three lines.
 */
export function StoreCard({ store }: StoreCardProps) {
  const { t } = useTranslation('stores');
  const titleId = useId();

  const { city, province } = store.address;

  return (
    // An `article` named by its own heading: the grid reads as a list of named regions rather than a
    // pile of boxes.
    <Card as="article" aria-labelledby={titleId} className="h-100">
      <Card.Body className="d-flex flex-column">
        {/* Dónde está, antes que cómo se llama: quien busca tienda busca ciudad, y es lo único de la
            tarjeta que lleva el verde de marca. El separador va en la traducción y no pegado en
            código: es puntuación, y de la puntuación decide cada idioma. */}
        <p className="overline store-card__region mb-1">{t('card.region', { city, province })}</p>
        <Card.Title as="h3" id={titleId} className="h5 mb-3">
          {store.name}
        </Card.Title>

        <StoreAddress address={store.address} className="store-card__address text-body-secondary mb-3" />

        {/* Las dos acciones al pie, separadas de los datos por una línea. `mt-auto` es lo que las
            alinea entre tarjetas de distinto alto. */}
        <div className="store-card__actions mt-auto d-flex flex-wrap align-items-center gap-3">
          <StorePhoneLink store={store} />

          {/* Sale del sitio, así que se abre aparte y el nombre accesible lo avisa — el icono solo no
              lo diría. */}
          <a
            className="d-inline-flex align-items-center gap-1"
            href={directionsUrl(store)}
            target="_blank"
            rel="noopener noreferrer"
            aria-label={t('card.directionsLabel', { store: store.name })}
          >
            {t('card.directions')}
            <Icon name="arrow_forward" size="sm" />
          </a>
        </div>
      </Card.Body>
    </Card>
  );
}
