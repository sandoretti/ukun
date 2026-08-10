import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { Section } from './Section';

/** An identifier and not a literal in the JSX: the i18n lint rule covers the tests too. */
const CONTENT = 'contenido';

/** The band itself: `<section>` has no implicit role until it is named, so this is the way in. */
const band = () => screen.getByText(CONTENT).closest('section');

describe('Section', () => {
  it('is a band with a container inside, and the content on the grid', () => {
    render(<Section>{CONTENT}</Section>);

    expect(band()?.className).toContain('section');
    // The content never hangs off the `<section>` directly: it goes inside the container that puts
    // it back on the grid, which is the whole point of this component.
    expect(screen.getByText(CONTENT).className).toContain('container');
  });

  it('carries the fragment identifier, which is what the hero links to', () => {
    render(<Section id="categories">{CONTENT}</Section>);

    expect(band()?.getAttribute('id')).toBe('categories');
  });

  it.each([
    { tone: 'muted', expected: 'section--muted' },
    { tone: 'soft', expected: 'section--soft' },
    { tone: 'brand', expected: 'section--brand' },
  ] as const)('paints the $tone tone with a class of the theme', ({ tone, expected }) => {
    render(<Section tone={tone}>{CONTENT}</Section>);

    expect(band()?.className).toContain(expected);
  });

  it('leaves the default tone without a background class of its own', () => {
    render(<Section>{CONTENT}</Section>);

    expect(band()?.className).not.toContain('section--muted');
    expect(band()?.className.trim()).toBe('section section--regular');
  });

  it.each([
    { spacing: 'regular', expected: 'section--regular' },
    { spacing: 'spacious', expected: 'section--spacious' },
  ] as const)('breathes with the $spacing rhythm', ({ spacing, expected }) => {
    render(<Section spacing={spacing}>{CONTENT}</Section>);

    expect(band()?.className).toContain(expected);
  });

  it('takes the extra classes each side needs without losing its own', () => {
    render(
      <Section className="closing-cta" containerClassName="text-center">
        {CONTENT}
      </Section>,
    );

    expect(band()?.className).toContain('section--regular');
    expect(band()?.className).toContain('closing-cta');
    expect(screen.getByText(CONTENT).className).toContain('text-center');
  });
});
