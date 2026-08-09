import type { FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Col, Form, Row } from 'react-bootstrap';

import type { ProductFilters, ProductSort, ProductType } from '../api/productKeys';
import { SORT_OPTIONS, type CatalogFilterInput } from '../useCatalogParams';

type CatalogFiltersProps = {
  filters: ProductFilters;
  types: ProductType[];
  onApply: (filters: CatalogFilterInput) => void;
  onClear: () => void;
};

export function CatalogFilters({ filters, types, onApply, onClear }: CatalogFiltersProps) {
  const { t } = useTranslation('catalog');

  // The form is uncontrolled and seeded from the URL: the key makes it re-seed when the URL changes
  // from outside (the back button), without duplicating the listing's state into a `useState`.
  const formKey = [filters.name, filters.typeId, filters.priceMin, filters.priceMax, filters.sort].join('|');

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const typeId = String(data.get('typeId') ?? '');
    onApply({
      name: String(data.get('name') ?? '').trim(),
      typeId: typeId === '' ? null : Number(typeId),
      priceMin: String(data.get('priceMin') ?? '').trim(),
      priceMax: String(data.get('priceMax') ?? '').trim(),
      sort: String(data.get('sort') ?? '') as ProductSort,
    });
  }

  return (
    <Form key={formKey} onSubmit={handleSubmit} className="mb-4">
      <fieldset>
        <legend className="h6">{t('filters.legend')}</legend>
        <Row className="g-3 align-items-end">
          <Col xs={12} md={4}>
            <Form.Label htmlFor="catalog-filter-name">{t('filters.name')}</Form.Label>
            <Form.Control
              id="catalog-filter-name"
              name="name"
              type="search"
              autoComplete="off"
              placeholder={t('filters.namePlaceholder')}
              defaultValue={filters.name}
            />
          </Col>
          <Col xs={12} md={3}>
            <Form.Label htmlFor="catalog-filter-type">{t('filters.type')}</Form.Label>
            <Form.Select id="catalog-filter-type" name="typeId" defaultValue={filters.typeId ?? ''}>
              <option value="">{t('filters.allTypes')}</option>
              {types.map((type) => (
                <option key={type.id} value={type.id}>
                  {type.name}
                </option>
              ))}
            </Form.Select>
          </Col>
          <Col xs={6} md={2}>
            <Form.Label htmlFor="catalog-filter-price-min">{t('filters.priceMin')}</Form.Label>
            <Form.Control
              id="catalog-filter-price-min"
              name="priceMin"
              type="number"
              min="0"
              step="0.01"
              inputMode="decimal"
              defaultValue={filters.priceMin}
            />
          </Col>
          <Col xs={6} md={2}>
            <Form.Label htmlFor="catalog-filter-price-max">{t('filters.priceMax')}</Form.Label>
            <Form.Control
              id="catalog-filter-price-max"
              name="priceMax"
              type="number"
              min="0"
              step="0.01"
              inputMode="decimal"
              defaultValue={filters.priceMax}
            />
          </Col>
          <Col xs={12} md={3}>
            <Form.Label htmlFor="catalog-filter-sort">{t('filters.sort')}</Form.Label>
            <Form.Select id="catalog-filter-sort" name="sort" defaultValue={filters.sort}>
              {SORT_OPTIONS.map((option) => (
                <option key={option} value={option}>
                  {t(`sort.${option}`)}
                </option>
              ))}
            </Form.Select>
          </Col>
          <Col xs={12} md="auto" className="d-flex gap-2">
            <Button type="submit" variant="primary">
              {t('filters.apply')}
            </Button>
            <Button type="button" variant="outline-secondary" onClick={onClear}>
              {t('filters.clear')}
            </Button>
          </Col>
        </Row>
      </fieldset>
    </Form>
  );
}
