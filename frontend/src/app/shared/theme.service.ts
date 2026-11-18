import { Injectable, signal } from '@angular/core';
import { Theme, shownTheme, toggledTheme } from './theme';

const STORAGE_KEY = 'theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<Theme>(shownTheme(stored(), matchMedia('(prefers-color-scheme: dark)').matches));

  constructor() {
    this.apply();
  }

  toggle(): void {
    this.theme.set(toggledTheme(this.theme()));
    try {
      localStorage.setItem(STORAGE_KEY, this.theme());
    } catch {
      // Stockage bloqué : le choix vaut pour cette visite seulement.
    }
    this.apply();
  }

  private apply(): void {
    document.documentElement.style.colorScheme = this.theme();
  }
}

function stored(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}
