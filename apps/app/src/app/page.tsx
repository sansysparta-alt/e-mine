import { Alerte, Bouton } from "@emine/ui";
import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { lireSession } from "@/serveur/api";
import { adresseConnexion, MOTIFS, type Motif } from "@/serveur/connexion";
import { PAGE_APRES_CONNEXION } from "@/serveur/retour";

type Props = { searchParams: Promise<Record<string, string | string[] | undefined>> };

/**
 * Racine de l'application : il n'y a pas de page intermédiaire. Connecté : tableau de bord ;
 * sinon, page de connexion Keycloak (seule porte d'entrée), avec le message demandé. Seule
 * exception : le service de connexion est injoignable, on le dit et on propose de réessayer.
 */
export default async function Racine({ searchParams }: Props) {
  const p = await searchParams;
  if (p.erreur === "indisponible") return <ConnexionIndisponible />;
  const session = await lireSession();
  if (session.jetonAcces) redirect(PAGE_APRES_CONNEXION);
  // Anciennes adresses (« /?deconnecte=1 », « /?expiree=1 ») : même message sur la page de connexion.
  const motif = MOTIFS.find((m) => p[m] !== undefined || p.motif === m) as Motif | undefined;
  redirect(motif ? adresseConnexion(motif) : "/api/auth/login");
}

/** Service de connexion injoignable : le seul écran de l'application hors connexion. */
async function ConnexionIndisponible() {
  const t = await getTranslations("connexion");
  return (
    <main
      id="contenu"
      className="mx-auto flex min-h-dvh max-w-xl flex-col justify-center gap-6 bg-fond px-4 py-10"
    >
      <p className="font-titre text-2xl font-bold text-royal">e-mine</p>
      <Alerte nature="erreur" titre={t("messages.indisponible.titre")}>
        {t("messages.indisponible.conseil")}
      </Alerte>
      <div>
        <Bouton asChild taille="grande">
          <a href="/api/auth/login">{t("reessayer")}</a>
        </Bouton>
      </div>
      <p className="text-sm text-texte-doux">{t("demo")}</p>
    </main>
  );
}
