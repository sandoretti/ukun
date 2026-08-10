import type { ReactNode } from 'react';
import { Container } from 'react-bootstrap';

/**
 * The background of the band. Named variants over a class of the theme, never a colour written at
 * the call site (`docs/07-frontend-ux-ui.md §Qué se descarta`) — and never a stack of booleans like
 * `<Section muted brand>`, which would allow combinations that mean nothing.
 */
const TONE_CLASS = {
  default: '',
  muted: 'section--muted',
  soft: 'section--soft',
  brand: 'section--brand',
} as const;

/** The two vertical rhythms of the site: 3rem for a band that is read at a glance, 4rem for a long one. */
const SPACING_CLASS = {
  regular: 'section--regular',
  spacious: 'section--spacious',
} as const;

type SectionProps = {
  /** Fragment identifier, when something links to this band (the hero's secondary call to action). */
  id?: string;
  tone?: keyof typeof TONE_CLASS;
  spacing?: keyof typeof SPACING_CLASS;
  /** Extra classes for the band itself, for the odd modifier only one section needs. */
  className?: string;
  /** Extra classes for the inner container — capping the measure of a column of prose, centring it. */
  containerClassName?: string;
  children: ReactNode;
};

/**
 * One horizontal band of a page: a `<section>` at full bleed with a `Container` inside that puts the
 * content back on the grid. It is the shape every block of the landing page repeats, and pulling it
 * up here is what keeps its vertical rhythm and its palette from being re-decided — slightly
 * differently each time — in every new block.
 */
export function Section({
  id,
  tone = 'default',
  spacing = 'regular',
  className = '',
  containerClassName,
  children,
}: SectionProps) {
  const classes = ['section', SPACING_CLASS[spacing], TONE_CLASS[tone], className]
    .filter(Boolean)
    .join(' ');

  return (
    <section id={id} className={classes}>
      <Container className={containerClassName}>{children}</Container>
    </section>
  );
}
