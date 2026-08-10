import { useTranslation } from 'react-i18next';
import { Col, Row } from 'react-bootstrap';

import type { Store } from '../../../shared/api/stores';
import { OnlineStoreBanner } from './OnlineStoreBanner';
import { StoreCard } from './StoreCard';

type StoreResultsProps = {
  /** Already read and already filtered: the four states are the screen's `QueryBoundary`. */
  stores: readonly Store[];
};

/**
 * The successful case of the listing: the online store on top and the ones you can walk into below.
 *
 * Exactly one row has `isOnline` (`ux_store_online`), so this is a `find` and not a partition that
 * has to cope with two. The filter can leave either side empty, and neither block is painted over
 * nothing — a heading above an empty grid is worse than no heading.
 */
export function StoreResults({ stores }: StoreResultsProps) {
  const { t } = useTranslation('stores');

  const online = stores.find((store) => store.isOnline);
  const physical = stores.filter((store) => !store.isOnline);

  return (
    <>
      {online !== undefined && <OnlineStoreBanner store={online} />}

      {physical.length > 0 && (
        <>
          <h2 className="h4 mb-4">{t('directory.title')}</h2>
          {/* En el orden que manda el API, que ya viene por nombre: la pantalla no aplica un segundo
              criterio por su cuenta. */}
          <Row xs={1} md={2} xl={3} className="g-4">
            {physical.map((store) => (
              <Col key={store.id}>
                <StoreCard store={store} />
              </Col>
            ))}
          </Row>
        </>
      )}
    </>
  );
}
