import { useEffect, useState } from 'react';

/**
 * The value, delayed until it stops changing for `delay` milliseconds. What it is for here is the
 * typeahead: the input stays instant for whoever is typing, and the request only follows the pauses
 * — a keystroke is not a query.
 */
export function useDebouncedValue<T>(value: T, delay = 250): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);

  return debounced;
}
