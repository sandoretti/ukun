import { Suspense } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, NavLink, Outlet } from 'react-router-dom';
import { Container, Form, Nav, Navbar } from 'react-bootstrap';

import { useLocaleSync } from '../shared/hooks/useLocaleSync';
import { LoadingState } from '../shared/ui/LoadingState';

const LANGUAGES = ['es', 'en'] as const;

export function AppLayout() {
  const { t, i18n } = useTranslation('common');
  useLocaleSync();

  return (
    <>
      <Navbar bg="light" expand="md" className="mb-4">
        <Container>
          <Navbar.Brand as={Link} to="/">
            {t('title')}
          </Navbar.Brand>
          <Navbar.Toggle aria-label={t('nav.toggle')} />
          <Navbar.Collapse>
            <Nav className="me-auto">
              <Nav.Link as={NavLink} to="/products">
                {t('nav.catalog')}
              </Nav.Link>
            </Nav>
            <Form.Group controlId="language-selector" className="d-flex align-items-center gap-2">
              <Form.Label className="mb-0 small">{t('language.label')}</Form.Label>
              <Form.Select
                size="sm"
                value={i18n.resolvedLanguage ?? 'es'}
                onChange={(event) => void i18n.changeLanguage(event.target.value)}
              >
                {LANGUAGES.map((language) => (
                  <option key={language} value={language}>
                    {t(`language.${language}`)}
                  </option>
                ))}
              </Form.Select>
            </Form.Group>
          </Navbar.Collapse>
        </Container>
      </Navbar>
      <Container as="main">
        <Suspense fallback={<LoadingState />}>
          <Outlet />
        </Suspense>
      </Container>
    </>
  );
}
