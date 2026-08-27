import { useCallback, useState } from 'react';

/** Where the choice is remembered. Namespaced because the key is shared with the whole origin. */
const STORAGE_KEY = 'ukun.backoffice.rail';

const EXPANDED = 'expanded';

/**
 * Reads the remembered width, defaulting to the mini rail.
 *
 * Every access is guarded: `localStorage` **throws** rather than returning `null` when the browser
 * blocks site data —a private window with third-party storage off, an embedded preview— and an
 * unguarded read in a hook body takes the whole back-office down with it. The default is the same one
 * a first visit gets, which is the point: nothing here is worth an error screen.
 */
function readExpanded(): boolean {
  try {
    return window.localStorage.getItem(STORAGE_KEY) === EXPANDED;
  } catch {
    return false;
  }
}

function rememberExpanded(expanded: boolean): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, expanded ? EXPANDED : 'collapsed');
  } catch {
    // Not being able to remember it is not a failure worth telling anybody about: the rail works
    // either way, and the choice simply lasts as long as the tab does.
  }
}

/**
 * Whether the back-office rail is showing its captions, remembered between visits.
 *
 * It is UI state and belongs in the browser rather than in the URL: unlike a filter or a page, the
 * width of the rail is a preference of whoever is looking and not a property of what is on screen —
 * sending somebody a link should not fold their sidebar. It is per browser on purpose too, which is
 * why it is `localStorage` and not `customer.locale`: a person can want the captions on the laptop
 * they work on and the icons on the tablet at the counter.
 */
export function useRailExpanded(): { expanded: boolean; toggle: () => void } {
  const [expanded, setExpanded] = useState(readExpanded);

  const toggle = useCallback(
    () =>
      setExpanded((current) => {
        rememberExpanded(!current);
        return !current;
      }),
    [],
  );

  return { expanded, toggle };
}
