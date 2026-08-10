import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import i18n from '../../i18n/config';
import { renderWithProviders } from '../../test/renderWithProviders';
import { LanguageSwitcher } from './LanguageSwitcher';

// The i18n instance is shared by the whole suite, so a test that switches language puts it back.
afterEach(async () => {
  await i18n.changeLanguage('es');
});

describe('LanguageSwitcher', () => {
  it('names each option by the language, not by its two-letter abbreviation', () => {
    renderWithProviders(<LanguageSwitcher />);

    // "ES" is an abbreviation on screen; the accessible name has to be the language itself.
    expect(screen.getByRole('button', { name: 'Español' })).toBeDefined();
    expect(screen.getByRole('button', { name: 'Inglés' })).toBeDefined();
    expect(screen.getByRole('group', { name: 'Idioma' })).toBeDefined();
  });

  it('marks the active language as pressed, so the state is not carried by colour alone', () => {
    renderWithProviders(<LanguageSwitcher />);

    expect(screen.getByRole('button', { name: 'Español' }).getAttribute('aria-pressed')).toBe('true');
    expect(screen.getByRole('button', { name: 'Inglés' }).getAttribute('aria-pressed')).toBe('false');
  });

  it('switches the language and moves the pressed state with it', async () => {
    const user = userEvent.setup();
    renderWithProviders(<LanguageSwitcher />);

    await user.click(screen.getByRole('button', { name: 'Inglés' }));

    expect(i18n.resolvedLanguage).toBe('en');
    expect(screen.getByRole('button', { name: 'English' }).getAttribute('aria-pressed')).toBe('true');
    expect(screen.getByRole('button', { name: 'Spanish' }).getAttribute('aria-pressed')).toBe('false');
  });
});
