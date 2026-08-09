import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Col, Container, Row } from 'react-bootstrap';

/**
 * The foot of every route, mounted once from `AppLayout`. It lives in `shared/ui/` and not in
 * `features/home/` because it is part of the shell, like the navigation bar.
 *
 * It only links to routes that exist. The stores block is deliberately text with no link: the stores
 * screen is issue #20 and is not merged yet, and a link to a blank screen is worse than no link.
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
                <li>
                  <Link to="/" className="site-footer__link">
                    {t('footer.explore.home')}
                  </Link>
                </li>
                <li>
                  <Link to="/products" className="site-footer__link">
                    {t('footer.explore.catalog')}
                  </Link>
                </li>
                <li>
                  <Link to="/products?sort=PRICE_DESC" className="site-footer__link">
                    {t('footer.explore.premium')}
                  </Link>
                </li>
              </ul>
            </nav>
          </Col>

          <Col md={3} sm={6}>
            <h2 className="h6 text-uppercase mb-3">{t('footer.stores.title')}</h2>
            <p className="mb-0">{t('footer.stores.body')}</p>
          </Col>
        </Row>

        <p className="site-footer__legal mb-0 py-3 small">
          {t('footer.legal', { year })}
        </p>
      </Container>
    </footer>
  );
}
