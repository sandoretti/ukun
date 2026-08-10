import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';

import { useProductTypes } from '../../../shared/api/productTypes';
import { Icon } from '../../../shared/ui/Icon';
import { QueryBoundary } from '../../../shared/ui/QueryBoundary';
import { Section } from '../../../shared/ui/Section';
import { SectionHeading } from '../../../shared/ui/SectionHeading';

type FeaturedCategoriesProps = {
  /** Anchor the hero's secondary call to action jumps to. */
  id: string;
};

/**
 * The catalogue's product types, straight from `GET /api/product-types`, each one a link into the
 * listing already filtered by it. They are read data and therefore cover the four states through
 * `QueryBoundary` (`docs/07-frontend-ux-ui.md`): a failure here is a section that says so, not a
 * home page that quietly shows a hardcoded list of categories that may no longer exist.
 *
 * The query lives in `shared/api/` and is the very same cache entry the catalogue's type filter
 * uses, so arriving at the listing from here costs no second request.
 *
 * They are laid out as an index — one row per category across the full width — and not as a grid of
 * cards: the name gets the room to be read at a glance, and one more category is one more row
 * instead of a hole in the last line of the grid. A `<ul>` and not a `<ol>`, because the order the
 * API returns them in carries no meaning worth announcing.
 */
export function FeaturedCategories({ id }: FeaturedCategoriesProps) {
  const { t } = useTranslation('home');
  const typesQuery = useProductTypes();

  return (
    <Section id={id} tone="muted">
      <SectionHeading title={t('categories.title')} lead={t('categories.lead')} />

      <QueryBoundary query={typesQuery} data={typesQuery.data} empty={t('categories.empty')}>
        {(types) => (
          <ul className="category-index list-unstyled mb-0">
            {types.map((type) => (
              <li key={type.id}>
                <Link to={`/products?typeId=${type.id}`} className="category-index__row">
                  <span className="category-index__name">{type.name}</span>
                  <Icon name="arrow_forward" className="category-index__arrow" />
                </Link>
              </li>
            ))}
          </ul>
        )}
      </QueryBoundary>
    </Section>
  );
}
