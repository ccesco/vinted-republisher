import { describe, expect, it } from 'vitest';
import { shownTheme, toggledTheme } from './theme';

describe('shown theme', () => {
  it('follows the system when no theme was chosen', () => {
    expect(shownTheme(null, true)).toBe('dark');
    expect(shownTheme(null, false)).toBe('light');
  });

  it('keeps the chosen theme whatever the system says', () => {
    expect(shownTheme('light', true)).toBe('light');
    expect(shownTheme('dark', false)).toBe('dark');
  });

  it('ignores an unknown stored value', () => {
    expect(shownTheme('purple', true)).toBe('dark');
  });
});

describe('toggled theme', () => {
  it('switches between light and dark', () => {
    expect(toggledTheme('light')).toBe('dark');
    expect(toggledTheme('dark')).toBe('light');
  });
});
