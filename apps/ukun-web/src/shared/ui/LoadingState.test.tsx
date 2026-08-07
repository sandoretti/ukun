import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { LoadingState } from './LoadingState';

// Mock react-i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

describe('LoadingState', () => {
  it('renders a status role with localized aria-label', () => {
    render(<LoadingState />);
    // Since we mock t() to return the key 'loading'
    const statusElement = screen.getByRole('status', { name: 'loading' });
    expect(statusElement).toBeDefined();
  });
});
