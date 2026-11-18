import { HttpErrorResponse } from '@angular/common/http';
import { Observable, firstValueFrom } from 'rxjs';

// Réponse d'erreur de l'API lue comme un objet (refus du contrat), ou null si elle n'en porte pas.
export async function refusalOf<T = Record<string, unknown>>(failure: unknown): Promise<T | null> {
  if (!(failure instanceof HttpErrorResponse)) {
    return null;
  }
  const body: unknown = failure.error instanceof Blob ? await parsed(failure.error) : failure.error;
  return body !== null && typeof body === 'object' && !(body instanceof ProgressEvent) ? body as T : null;
}

export function statusOf(failure: unknown): number {
  return failure instanceof HttpErrorResponse ? failure.status : 0;
}

export function answerOf<T>(request: Observable<T>): Promise<T> {
  return firstValueFrom(request);
}

async function parsed(blob: Blob): Promise<unknown> {
  try {
    return JSON.parse(await blob.text());
  } catch {
    return null;
  }
}
