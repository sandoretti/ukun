import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Card, Container } from 'react-bootstrap';

import { Icon } from '../../shared/ui/Icon';
import { ADMIN_PRODUCTS_PATH } from './adminRoutes';
import { ADMIN_EMPLOYEES_PATH } from './employees/routes';

/**
 * Where the administration screens live, at `/admin`.
 *
 * It exists because `/admin/*` is a branch and not a screen: what talks to `/api/admin/**` is an
 * administrator's alone (ADR-0020), and the session menu needs one place to send them rather than a
 * growing list of privileged entries in the bar.
 *
 * **It lists only what exists.** Product types (RF-13) are still being built and will add their own
 * entry when they have a screen to point at; announcing them here first would be a menu that lies
 * about what the back-office can do today. Employees (RF-11) arrived with their two screens and took
 * the entry this paragraph was holding for them.
 */
export default function AdminHomePage() {
  const { t } = useTranslation('backoffice');

  return (
    <Container className="py-5">
      <div className="col-lg-8">
        <h1 className="h2 mb-2">{t('admin.title')}</h1>
        <p className="text-body-secondary mb-4">{t('admin.lead')}</p>

        {/* A link that wears a card, not a card with a link inside: the whole thing is one
            destination, so it is one control with one accessible name. */}
        <div className="d-grid gap-3">
          <Card as={Link} to={ADMIN_PRODUCTS_PATH} className="text-decoration-none">
            <Card.Body className="d-flex align-items-start gap-3">
              <Icon name="inventory_2" size="lg" className="text-primary" />
              <div>
                <Card.Title as="h2" className="h5 mb-1">
                  {t('admin.products.title')}
                </Card.Title>
                <Card.Text className="text-body-secondary mb-0">{t('admin.products.body')}</Card.Text>
              </div>
            </Card.Body>
          </Card>

          <Card as={Link} to={ADMIN_EMPLOYEES_PATH} className="text-decoration-none">
            <Card.Body className="d-flex align-items-start gap-3">
              <Icon name="badge" size="lg" className="text-primary" />
              <div>
                <Card.Title as="h2" className="h5 mb-1">
                  {t('admin.employees.title')}
                </Card.Title>
                <Card.Text className="text-body-secondary mb-0">{t('admin.employees.body')}</Card.Text>
              </div>
            </Card.Body>
          </Card>
        </div>
      </div>
    </Container>
  );
}
