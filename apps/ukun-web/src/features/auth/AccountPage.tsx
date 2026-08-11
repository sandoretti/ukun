import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Col, Container, Row } from 'react-bootstrap';

import { useAuth } from '../../shared/hooks/useAuth';
import { ORDERS_PATH, PROFILE_PATH } from '../../shared/routes';
import { Icon } from '../../shared/ui/Icon';
import { SignOutForm } from './components/SignOutForm';

/**
 * What the browser is signed in as, at `/account`: the identity half of `GET /api/me`, and the way
 * out. The customer profile —name, address, card, favourite store— is a different screen against a
 * different endpoint (`GET /api/me/profile`, RF-05), linked from here.
 *
 * It reads nothing of its own: the session probe is already in the cache, asked for by the shell.
 */
export default function AccountPage() {
  const { t } = useTranslation(['common', 'profile', 'orders']);
  const { user } = useAuth();

  // `RequireAuth` is what guarantees there is a user; this is the type narrowing, not a state.
  if (user === undefined) {
    return null;
  }

  return (
    <Container className="py-5">
      <Row>
        <Col lg={7}>
          <h1 className="h2 mb-4">{t('account.title')}</h1>

          <dl className="row mb-4">
            <dt className="col-sm-4 text-body-secondary fw-normal">{t('account.email')}</dt>
            <dd className="col-sm-8">{user.email}</dd>

            <dt className="col-sm-4 text-body-secondary fw-normal">{t('account.roles')}</dt>
            {/* Un rol es un valor cerrado del contrato, así que se traduce; nunca se pinta el
                identificador crudo que viaja en el JSON. */}
            <dd className="col-sm-8">
              {user.roles.map((role) => t(`account.role.${role}`)).join(', ')}
            </dd>
          </dl>

          {/* Los datos de cliente y los pedidos son otros recursos y otras pantallas; desde aquí
              solo se va a ellas. */}
          <div className="d-flex flex-wrap gap-2 mb-4">
            <Link to={PROFILE_PATH} className="btn btn-outline-primary btn-sm d-inline-flex align-items-center gap-2">
              <Icon name="edit" size="sm" />
              {t('profile:link')}
            </Link>
            <Link to={ORDERS_PATH} className="btn btn-outline-primary btn-sm d-inline-flex align-items-center gap-2">
              <Icon name="receipt_long" size="sm" />
              {t('orders:link')}
            </Link>
          </div>

          <div>
            <SignOutForm variant="primary" />
          </div>
        </Col>
      </Row>
    </Container>
  );
}
