import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { ResultCount } from './ResultCount';

/** The live region, which has no role of its own to be queried by. */
const region = (container: HTMLElement) => container.querySelector('[aria-live="polite"]');

describe('ResultCount', () => {
  it('says how many were found', () => {
    render(<ResultCount label="12 tipos" />);

    expect(screen.getByText('12 tipos')).toBeDefined();
  });

  it('keeps the live region on the page while the count is still unknown', () => {
    const { container, rerender } = render(<ResultCount />);

    // The region has to exist *before* the number arrives. One that appears already filled is one
    // assistive technology was not watching, so the count would never be announced.
    expect(region(container)).not.toBeNull();
    expect(region(container)?.textContent).toBe('');

    rerender(<ResultCount label="3 empleados" />);

    expect(region(container)?.textContent).toBe('3 empleados');
  });
});
