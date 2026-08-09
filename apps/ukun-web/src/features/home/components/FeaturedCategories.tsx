import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Col, Container, Row } from 'react-bootstrap';

import { toProblem } from '../../../shared/api/http';
import { useProductTypes } from '../../../shared/api/productTypes';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { ErrorState } from '../../../shared/ui/ErrorState';
import { LoadingState } from '../../../shared/ui/LoadingState';

type FeaturedCategoriesProps = {
  /** Anchor the hero's secondary call to action jumps to. */
  id: string;
};

/**
 * The catalogue's product types, straight from `GET /api/product-types`, each one a link into the
 * listing already filtered by it. They are read data and therefore cover the four states
 * (`docs/07-frontend-ux-ui.md`): a failure here is a section that says so, not a home page that
 * quietly shows a hardcoded list of categories that may no longer exist.
 *
 * The query lives in `shared/api/` and is the very same cache entry the catalogue's type filter
 * uses, so arriving at the listing from here costs no second request.
 */
export function FeaturedCategories({ id }: FeaturedCategoriesProps) {
  const { t } = useTranslation('home');
  const typesQuery = useProductTypes();
  const types = typesQuery.data ?? [];

  return (
    <section id={id} className="bg-body-tertiary py-5">
      <Container>
        <h2 className="h3 mb-2">{t('categories.title')}</h2>
        <p className="text-body-secondary mb-4">{t('categories.lead')}</p>

        {typesQuery.isPending ? (
          <LoadingState />
        ) : typesQuery.isError ? (
          <ErrorState
            code={toProblem(typesQuery.error).code}
            extensions={toProblem(typesQuery.error).extensions}
          />
        ) : types.length === 0 ? (
          <EmptyState message={t('categories.empty')} />
        ) : (
          <Row as="ul" xs={2} md={3} lg={4} className="g-3 list-unstyled mb-0">
            {types.map((type) => (
              <Col as="li" key={type.id}>
                <Link to={`/products?typeId=${type.id}`} className="category-tile">
                  <span className="category-tile__name">{type.name}</span>
                </Link>
              </Col>
            ))}
          </Row>
        )}
      </Container>
    </section>
  );
}
