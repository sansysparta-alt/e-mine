import { Bouton, Carte } from "@emine/ui";
import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { MessageConnexion, motifDepuisParametres } from "@/composants/message-connexion";
import { lireSession } from "@/serveur/api";
import { PAGE_APRES_CONNEXION } from "@/serveur/retour";

type Props = { searchParams: Promise<Record<string, string | string[] | undefined>> };

/** Page de connexion (publique). Un utilisateur déjà connecté va directement à son tableau de bord. */
export default async function Connexion({ searchParams }: Props) {
  const parametres = await searchParams;
  const motif = motifDepuisParametres(parametres);
  const session = await lireSession();
  if (session.jetonAcces && motif === null) {
    redirect(PAGE_APRES_CONNEXION);
  }
  const t = await getTranslations("connexion");
  return (
    <main
      id="contenu"
      className="mx-auto flex min-h-dvh max-w-xl flex-col justify-center gap-6 px-4 py-10 sm:px-6"
    >
      <p className="font-titre text-2xl font-bold text-royal">e-mine</p>
      <h1 className="text-3xl sm:text-4xl">{t("titre")}</h1>
      <MessageConnexion motif={motif} />
      <Carte>
        <p className="text-lg text-texte-doux">{t("presentation")}</p>
        <Bouton asChild taille="grande" className="mt-5 w-full sm:w-auto">
          <a href="/api/auth/login">{t("bouton")}</a>
        </Bouton>
        <p className="mt-3 text-sm text-texte-doux">{t("aide")}</p>
      </Carte>
      <p className="text-sm text-texte-doux">{t("demo")}</p>
    </main>
  );
}
