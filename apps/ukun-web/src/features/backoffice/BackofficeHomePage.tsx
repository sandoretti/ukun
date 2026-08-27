import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Card } from 'react-bootstrap';

import { useAuth } from '../../shared/hooks/useAuth';
import { Icon } from '../../shared/ui/Icon';
import { PageHeader } from '../../shared/ui/PageHeader';
import { BACKOFFICE_SECTIONS } from './sections';

/**
 * Where the back-office begins, at `/backoffice`.
 *
 * It exists because the back-office is a **place** and not a screen: the session menu of
 * `features/auth` carries one entry, and it lands here whatever the roles are. What the account may
 * open is decided once, from `BACKOFFICE_SECTIONS` through `useAuth().hasRole` — the same list the
 * rail reads, so a card and a rail entry can never disagree about what exists.
 *
 * It replaces the old `/admin` index, which listed the catalogue and the staff and had no idea the
 * stock screens existed: they hung off the other branch and were reached from the bar. That is the
 * split this screen closes.
 *
 * The cards are not the navigation —the rail is, on every screen of the back-office— they are the
 * landing: what this account can do, said in a sentence each, which a row of icons cannot do.
 */
export default function BackofficeHomePage() {
  const { t } = useTranslation('backoffice');
  const { hasRole } = useAuth();

  const sections = BACKOFFICE_SECTIONS.filter((section) => hasRole(section.role));

  return (
    <div className="col-lg-8">
      <PageHeader title={t('home.title')} lead={t('home.lead')} />

      {/* A link wearing a card, not a card with a link inside: the whole thing is one destination, so
          it is one control with one accessible name. */}
      <div className="d-grid gap-3 mt-4">
        {sections.map((section) => (
          <Card key={section.key} as={Link} to={section.to} className="text-decoration-none">
            <Card.Body className="d-flex align-items-start gap-3">
              <Icon name={section.icon} size="lg" className="text-primary" />
              <div>
                <Card.Title as="h2" className="h5 mb-1">
                  {t(section.titleKey)}
                </Card.Title>
                <Card.Text className="text-body-secondary mb-0">{t(section.bodyKey)}</Card.Text>
              </div>
            </Card.Body>
          </Card>
        ))}
      </div>
    </div>
  );
}
