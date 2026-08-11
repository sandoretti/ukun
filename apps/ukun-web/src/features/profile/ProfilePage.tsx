import { useTranslation } from 'react-i18next';
import { Col, Container, Row } from 'react-bootstrap';

import { ResourceBoundary } from '../../shared/ui/ResourceBoundary';
import { useMyProfile } from './api/queries';
import { ProfileForm } from './components/ProfileForm';

/**
 * The customer profile (RF-05), at `/profile` and behind `RequireAuth`.
 *
 * The screen's own job is small on purpose: read one resource, cover the four states with the base
 * components, and hand the profile over to the form. The measure is capped to a single column —a
 * form read at arm's length is a column, not a page — and everything else is the form's business.
 */
export default function ProfilePage() {
  const { t } = useTranslation('profile');
  const profileQuery = useMyProfile();

  return (
    <Container className="py-5">
      <Row>
        <Col lg={9} xl={8}>
          <h1 className="h2 mb-2">{t('title')}</h1>
          <p className="text-body-secondary mb-4">{t('lead')}</p>

          <ResourceBoundary query={profileQuery} data={profileQuery.data} empty={t('empty')}>
            {(profile) => <ProfileForm profile={profile} />}
          </ResourceBoundary>
        </Col>
      </Row>
    </Container>
  );
}
