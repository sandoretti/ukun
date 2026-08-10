import { useTranslation } from 'react-i18next';
import { Card } from 'react-bootstrap';

import type { Store } from '../../../shared/api/stores';
import { Icon } from '../../../shared/ui/Icon';
import { directionsUrl } from '../directions';
import { formatPhone } from '../formatPhone';

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

  const titleId = `store-${store.id}-title`;
  const { street, postalCode, city, province } = store.address;
  // Built here and not inside the JSX so each line of the address stays one node. Spanish five-digit
  // postal code, no country: it is what the contract's `Address` carries.
  const locality = `${postalCode} ${city}`;
  const region = `${city} · ${province}`;

  return (
    // An `article` named by its own heading: the grid reads as a list of named regions rather than a
    // pile of boxes.
    <Card as="article" aria-labelledby={titleId} className="h-100">
      <Card.Body className="d-flex flex-column">
        {/* Dónde está, antes que cómo se llama: quien busca tienda busca ciudad, y es lo único de la
            tarjeta que lleva el verde de marca. */}
        <p className="overline store-card__region mb-1">{region}</p>
        <Card.Title as="h3" id={titleId} className="h5 mb-3">
          {store.name}
        </Card.Title>

        {/* `address` is the element for exactly this, and Bootstrap's reboot already keeps it from
            being italicised. One element per line instead of `<br>`: the break belongs to how the
            address is laid out, not to its content. */}
        <address className="store-card__address text-body-secondary mb-3">
          <span className="d-block">{street}</span>
          <span className="d-block">{locality}</span>
          <span className="d-block">{province}</span>
        </address>

        {/* Las dos acciones al pie, separadas de los datos por una línea. `mt-auto` es lo que las
            alinea entre tarjetas de distinto alto. */}
        <div className="store-card__actions mt-auto d-flex flex-wrap align-items-center gap-3">
          {/* El número es accionable, que en un móvil es media razón de ser de un listado de
              tiendas. El `href` lleva el E.164 crudo que entiende el marcador; el texto, la
              agrupación legible. El nombre accesible dice a qué tienda llama: si no, un lector de
              pantalla recita cinco números indistinguibles. */}
          <a
            className="text-tabular-nums"
            href={`tel:${store.phone}`}
            aria-label={t('card.call', { store: store.name })}
          >
            {formatPhone(store.phone)}
          </a>

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
