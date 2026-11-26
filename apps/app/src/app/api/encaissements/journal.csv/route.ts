import { relayer } from "@/serveur/relais";

const PARAMETRES = ["du", "au", "moyen", "objet"] as const;

/** Export CSV du journal des encaissements (relais BFF, US-04.11) : seuls les filtres passent. */
export async function GET(requete: Request) {
  const source = new URL(requete.url).searchParams;
  const q = new URLSearchParams();
  for (const p of PARAMETRES) {
    const v = source.get(p);
    if (v && /^[A-Z_0-9-]{1,20}$/i.test(v)) q.set(p, v);
  }
  return relayer(requete, `/api/v1/encaissements/journal.csv?${q.toString()}`);
}
