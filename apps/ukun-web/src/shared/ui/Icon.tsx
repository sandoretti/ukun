type IconProps = {
  name: string;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
};

/**
 * The app's only icon system (Material Symbols, self-hosted). The size comes from a theme class,
 * never from an inline `style` with pixels — `docs/07-frontend-ux-ui.md`. Icons are decorative by
 * definition here: whatever they mean is also written next to them, or in the `aria-label` of the
 * button that contains them.
 */
export function Icon({ name, size = 'md', className = '' }: IconProps) {
  return (
    <span className={`material-symbols-outlined icon-${size} ${className}`} aria-hidden="true">
      {name}
    </span>
  );
}
