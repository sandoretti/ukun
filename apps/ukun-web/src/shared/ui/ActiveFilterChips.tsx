import { useTranslation } from 'react-i18next';
import { Button } from 'react-bootstrap';

import { Icon } from './Icon';

export type FilterChip = {
  /** Which filter this chip removes. Opaque here; each screen names its own. */
  key: string;
  /** What is filtered, already in words: «Nombre: cama», «Solo administradores». */
  label: string;
};

type ActiveFilterChipsProps = {
  chips: FilterChip[];
  onRemove: (key: string) => void;
  onClear: () => void;
};

/**
 * What is filtered right now, one removable chip each, plus a way to clear the lot.
 *
 * It is what `docs/07-frontend-ux-ui.md` prescribes for every listing, and it beats a single "clear
 * filters" link for two reasons that matter more the longer the screen is looked at: it **says what
 * is filtered** —a table can be short because of a filter three controls away, or one that is off
 * screen on a phone— and it lets a reader drop **one** of them without starting over.
 *
 * Nothing is rendered when nothing is filtered: a row that says "no filters" is a row that only
 * takes up space.
 *
 * It knows nothing about what a filter *is*. The chips arrive already worded, because only the
 * screen can turn `isAdmin: false` into «Solo empleados» — which is also why `key` is a plain string
 * here and a typed key in each caller.
 */
export function ActiveFilterChips({ chips, onRemove, onClear }: ActiveFilterChipsProps) {
  const { t } = useTranslation('common');

  if (chips.length === 0) {
    return null;
  }

  return (
    <div
      role="group"
      aria-label={t('filters.active')}
      className="d-flex flex-wrap align-items-center gap-2"
    >
      {chips.map((chip) => (
        <Button
          key={chip.key}
          type="button"
          variant="light"
          size="sm"
          className="rounded-pill border d-inline-flex align-items-center gap-1"
          // The chip's own words are in the name, so twenty chips are twenty different controls to
          // a screen reader instead of twenty called "remove".
          aria-label={t('filters.remove', { label: chip.label })}
          onClick={() => onRemove(chip.key)}
        >
          {chip.label}
          <Icon name="close" size="sm" />
        </Button>
      ))}

      {/* Only worth offering once there is more than one thing to clear: with a single chip it would
          be a second button doing exactly what the first one does. */}
      {chips.length > 1 && (
        <Button type="button" variant="link" size="sm" className="link-secondary" onClick={onClear}>
          {t('filters.clearAll')}
        </Button>
      )}
    </div>
  );
}
