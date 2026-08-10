type IllustrationProps = {
  src: string;
  /** Width cap, from the theme: `md` for a drawing that shares the row, `sm` for one over a column. */
  size?: 'sm' | 'md';
  className?: string;
};

/**
 * A brand line drawing. It is **not** UI iconography: it does not go through `<Icon>` and does not
 * enter the icon map of `docs/07-frontend-ux-ui.md`.
 *
 * Always decorative (`alt=""`). Whatever the drawing suggests is written right next to it, so an
 * alternative text here would only make a screen reader say everything twice. A drawing that does
 * carry meaning of its own is a different component, not a boolean on this one.
 */
export function Illustration({ src, size = 'md', className = '' }: IllustrationProps) {
  return <img src={src} alt="" className={`illustration illustration--${size} ${className}`} />;
}
