import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Col, Container, Row } from 'react-bootstrap';

/**
 * Where the "Explore" block goes, in order. Data and not three near-identical `<li>`s, same as the
 * navigation bar's entries — and every destination is a route that exists today.
 */
const EXPLORE_LINKS = [
  { key: 'home', to: '/' },
  { key: 'catalog', to: '/products' },
  { key: 'premium', to: '/products?sort=PRICE_DESC' },
] as const satisfies readonly { key: string; to: string }[];

/**
 * The foot of every route, mounted once from `AppLayout`. It lives in `shared/ui/` and not in
 * `features/home/` because it is part of the shell, like the navigation bar.
 *
 * It only links to routes that exist — which now includes the stores screen (issue #20), so the
 * stores block, deliberately text with no link while there was nowhere to send anyone, finally has a
 * destination.
 */
export function SiteFooter() {
  const { t } = useTranslation('common');
  const year = new Date().getFullYear();

  return (
    <footer className="site-footer mt-auto">
      <Container>
        <Row className="g-4 py-5">
          <Col md={5}>
            <p className="site-footer__brand mb-2">{t('title')}</p>
            <p className="mb-0 pe-md-4">{t('footer.claim')}</p>
          </Col>

          <Col md={4} sm={6}>
            <h2 className="h6 text-uppercase mb-3">{t('footer.explore.title')}</h2>
            <nav aria-label={t('footer.explore.title')}>
              <ul className="list-unstyled mb-0 d-grid gap-2">
                {EXPLORE_LINKS.map((link) => (
                  <li key={link.key}>
                    <Link to={link.to} className="site-footer__link">
                      {t(`footer.explore.${link.key}`)}
                    </Link>
                  </li>
                ))}
              </ul>
            </nav>
          </Col>

          <Col md={3} sm={6}>
            <h2 className="h6 text-uppercase mb-3">{t('footer.stores.title')}</h2>
            <p className="mb-2">{t('footer.stores.body')}</p>
            <Link to="/stores" className="site-footer__link">
              {t('footer.stores.link')}
            </Link>
          </Col>
        </Row>

        <p className="site-footer__legal mb-0 py-3 small">
          {t('footer.legal', { year })}
        </p>
      </Container>
    </footer>
  );
}
