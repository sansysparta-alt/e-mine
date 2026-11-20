/**
 * Requête HTTP utilisée par le client généré. Appelée côté serveur uniquement (BFF) : le jeton
 * d'accès est passé dans les en-têtes par l'appelant et n'atteint jamais le navigateur.
 */
export class ErreurApi extends Error {
  constructor(
    readonly status: number,
    readonly code: string | undefined,
    message: string,
    /** Détails du ProblemDetail de l'API (champs invalides, éléments manquants…). */
    readonly details: Record<string, unknown> = {},
  ) {
    super(message);
    this.name = "ErreurApi";
  }
}

function baseUrl(): string {
  return process.env.API_BASE_URL ?? "http://localhost:8080";
}

export async function requete<T>(url: string, options: RequestInit = {}): Promise<T> {
  const reponse = await fetch(`${baseUrl()}${url}`, {
    ...options,
    headers: { Accept: "application/json", ...options.headers },
    cache: "no-store",
  });
  const texte = await reponse.text();
  const corps: unknown = texte ? JSON.parse(texte) : undefined;
  if (!reponse.ok) {
    const probleme = (corps ?? {}) as {
      code?: string;
      message?: string;
      detail?: string;
      details?: Record<string, unknown>;
    };
    throw new ErreurApi(
      reponse.status,
      probleme.code,
      probleme.message ?? probleme.detail ?? reponse.statusText,
      probleme.details ?? {},
    );
  }
  return { data: corps, status: reponse.status, headers: reponse.headers } as T;
}
