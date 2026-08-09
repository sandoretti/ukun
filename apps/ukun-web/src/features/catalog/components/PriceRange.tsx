import { useState, type CSSProperties, type KeyboardEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Col, Form, InputGroup, Row } from 'react-bootstrap';

import type { CatalogFilterInput } from '../api/productKeys';

type PriceRangeProps = {
  /** Current filter, as it travels in the URL: an empty string means "no bound on this end". */
  priceMin: string;
  priceMax: string;
  /** End of the track, read from the catalog itself (`useMaxProductPrice`). */
  ceiling: number;
  onCommit: (range: Pick<CatalogFilterInput, 'priceMin' | 'priceMax'>) => void;
};

const STEP = 10;

/**
 * Two-thumb price range with its two fields. The native control only has one thumb, so the slider
 * is two `input[type=range]` stacked on the same track — which keeps every keyboard behaviour for
 * free (each thumb is still a slider with its own accessible name) instead of rebuilding it on top
 * of a `div`. The fields are there because a slider cannot say "exactly 249,50": dragging is for
 * exploring, typing is for knowing what you want.
 *
 * Both write the same two strings, so there is one source of truth and no state to keep in sync.
 * Neither filters while it is being moved or typed: the listing changes when the thumb is released
 * or the field is left, so a drag across the track is one request and not forty.
 */
export function PriceRange({ priceMin, priceMax, ceiling, onCommit }: PriceRangeProps) {
  const { t } = useTranslation('catalog');

  const urlLow = priceMin === '' ? '0' : priceMin;
  const urlHigh = priceMax === '' ? String(ceiling) : priceMax;

  const [lowText, setLowText] = useState(urlLow);
  const [highText, setHighText] = useState(urlHigh);

  // The URL is the source of truth: a chip removed or the back button moves the thumbs back.
  const seed = `${urlLow}|${urlHigh}`;
  const [seeded, setSeeded] = useState(seed);
  if (seeded !== seed) {
    setSeeded(seed);
    setLowText(urlLow);
    setHighText(urlHigh);
  }

  const clamp = (value: number) => Math.min(Math.max(value, 0), ceiling);
  // An empty or half-typed field reads as its own end of the track, never as `NaN`.
  const toBound = (text: string, fallback: number) =>
    text.trim() === '' || Number.isNaN(Number(text)) ? fallback : clamp(Number(text));

  const low = toBound(lowText, 0);
  const high = toBound(highText, ceiling);

  function commit() {
    // Typing 800 in "from" and 200 in "to" is a range with its ends swapped, not an empty listing.
    const from = Math.min(low, high);
    const to = Math.max(low, high);
    setLowText(String(from));
    setHighText(String(to));

    // Both ends at their limit mean "no price filter", not "from 0 to the ceiling": the URL stays
    // clean and the chips do not show a filter nobody set.
    const next = { priceMin: from === 0 ? '' : String(from), priceMax: to >= ceiling ? '' : String(to) };
    if (next.priceMin !== priceMin || next.priceMax !== priceMax) {
      onCommit(next);
    }
  }

  function commitOnEnter(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Enter') {
      event.preventDefault();
      commit();
    }
  }

  const percentage = (value: number) => `${(value / ceiling) * 100}%`;

  return (
    <>
      <div
        className="price-range"
        // Dynamic value: the only case `docs/07-frontend-ux-ui.md` admits an inline style.
        style={{ '--range-start': percentage(low), '--range-end': percentage(high) } as CSSProperties}
      >
        <span className="price-range__track" aria-hidden="true" />
        <span className="price-range__fill" aria-hidden="true" />
        <input
          type="range"
          min={0}
          max={ceiling}
          step={STEP}
          value={low}
          aria-label={t('filters.priceMin')}
          onChange={(event) => setLowText(String(Math.min(Number(event.target.value), high)))}
          onPointerUp={commit}
          onKeyUp={commit}
          onBlur={commit}
        />
        <input
          type="range"
          min={0}
          max={ceiling}
          step={STEP}
          value={high}
          aria-label={t('filters.priceMax')}
          onChange={(event) => setHighText(String(Math.max(Number(event.target.value), low)))}
          onPointerUp={commit}
          onKeyUp={commit}
          onBlur={commit}
        />
      </div>

      <Row className="g-2 mt-1">
        <Col xs={6}>
          <Form.Label htmlFor="catalog-price-from" className="small text-body-secondary mb-1">
            {t('filters.priceFrom')}
          </Form.Label>
          <InputGroup size="sm">
            <Form.Control
              id="catalog-price-from"
              type="number"
              min={0}
              max={ceiling}
              step={STEP}
              inputMode="decimal"
              className="text-tabular-nums"
              value={lowText}
              onChange={(event) => setLowText(event.target.value)}
              onKeyDown={commitOnEnter}
              onBlur={commit}
            />
            <InputGroup.Text>€</InputGroup.Text>
          </InputGroup>
        </Col>
        <Col xs={6}>
          <Form.Label htmlFor="catalog-price-to" className="small text-body-secondary mb-1">
            {t('filters.priceTo')}
          </Form.Label>
          <InputGroup size="sm">
            <Form.Control
              id="catalog-price-to"
              type="number"
              min={0}
              max={ceiling}
              step={STEP}
              inputMode="decimal"
              className="text-tabular-nums"
              value={highText}
              onChange={(event) => setHighText(event.target.value)}
              onKeyDown={commitOnEnter}
              onBlur={commit}
            />
            <InputGroup.Text>€</InputGroup.Text>
          </InputGroup>
        </Col>
      </Row>
    </>
  );
}
