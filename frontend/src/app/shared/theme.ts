export type Theme = 'light' | 'dark';

// Le choix mémorisé l'emporte sur le réglage du système.
export function shownTheme(stored: string | null, systemPrefersDark: boolean): Theme {
  if (stored === 'light' || stored === 'dark') {
    return stored;
  }
  return systemPrefersDark ? 'dark' : 'light';
}

export function toggledTheme(theme: Theme): Theme {
  return theme === 'dark' ? 'light' : 'dark';
}
