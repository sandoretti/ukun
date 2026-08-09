import { useEffect, useRef } from 'react';

/** How far ahead of the viewport the sentinel counts as visible: loading starts before the bottom. */
const ROOT_MARGIN = '200px';

/**
 * Runs `onVisible` when the element the returned ref is put on enters the viewport. What it is for
 * here is the catalog's sentinel: scrolling to the end of the grid asks for the next chunk without
 * anyone pressing anything.
 *
 * It is an addition, never the only way in. With `enabled` false — nothing left to load, or a chunk
 * already in flight — no observer exists at all, and where there is no `IntersectionObserver` (jsdom,
 * an old browser) the hook does nothing and the button next to the sentinel stays the way through.
 */
export function useOnScreen(onVisible: () => void, enabled: boolean) {
  const sentinel = useRef<HTMLDivElement>(null);
  // Through a ref so a new closure on every render does not tear the observer down and build it up
  // — and rebuilding it is not free here: observing an element that is already on screen fires
  // straight away, which would turn every render into another chunk.
  const callback = useRef(onVisible);
  useEffect(() => {
    callback.current = onVisible;
  });

  useEffect(() => {
    const element = sentinel.current;
    if (!enabled || element === null || typeof IntersectionObserver === 'undefined') {
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          callback.current();
        }
      },
      { rootMargin: ROOT_MARGIN },
    );
    observer.observe(element);

    return () => observer.disconnect();
  }, [enabled]);

  return sentinel;
}
