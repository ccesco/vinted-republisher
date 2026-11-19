import type { AutomaticRunOverview } from '../../generated/api';

// Toute différence compte (US-42) : aucune liste de champs à tenir à jour, et une relance peut écrire dès son début.
export function runChanged(previous: AutomaticRunOverview | undefined, current: AutomaticRunOverview): boolean {
  return JSON.stringify(previous) !== JSON.stringify(current);
}

// Une action demandée par plusieurs suivis (série, scan…) n'a lieu qu'une fois par période : tout de suite si la
// période est passée, sinon une seule fois à sa fin, sans perdre la dernière demande.
export class OncePerPeriod {
  private lastRunAt?: number;
  private pending?: ReturnType<typeof setTimeout>;
  private closed = false;

  constructor(private readonly periodMs: number, private readonly action: () => void) {
  }

  request(): void {
    if (this.closed || this.pending !== undefined) {
      return;
    }
    const elapsed = this.lastRunAt === undefined ? this.periodMs : Date.now() - this.lastRunAt;
    if (elapsed >= this.periodMs) {
      this.run();
    } else {
      this.pending = setTimeout(() => {
        this.pending = undefined;
        this.run();
      }, this.periodMs - elapsed);
    }
  }

  // Définitif : une réponse arrivée après la sortie de l'écran ne relance plus rien.
  close(): void {
    this.closed = true;
    clearTimeout(this.pending);
    this.pending = undefined;
  }

  private run(): void {
    this.lastRunAt = Date.now();
    this.action();
  }
}
