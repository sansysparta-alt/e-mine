import { Bouton, Carte } from "@emine/ui";
import Link from "next/link";
import { useTranslations } from "next-intl";

export type VueTableauDeBordProps = {
  prenom: string;
  nom: string;
  profilLibelle: string;
  interne: boolean;
  /** Droit « déposer » (US-01.6) : affiche le bouton de nouvelle demande. */
  peutDeposer?: boolean;
};

/**
 * Tableau de bord (vide en J1) : salutation au nom de l'utilisateur, profil, et le bloc principal
 * adapté au type de profil (demandes pour un usager, dossiers à traiter pour un agent).
 */
export function VueTableauDeBord({
  prenom,
  nom,
  profilLibelle,
  interne,
  peutDeposer = false,
}: VueTableauDeBordProps) {
  const t = useTranslations("tableauDeBord");
  const bloc = interne ? "dossiers" : "demandes";
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-3xl sm:text-4xl">{t("bonjour", { prenom, nom })}</h1>
        <p className="mt-2 text-lg text-texte-doux">{t("profil", { libelle: profilLibelle })}</p>
      </div>
      <Carte titre={t(`${bloc}.titre`)}>
        <p className="font-semibold">{t(`${bloc}.vide`)}</p>
        <p className="mt-1 text-texte-doux">{t(`${bloc}.aide`)}</p>
        {peutDeposer ? (
          <Bouton asChild className="mt-4">
            <Link href="/demandes/nouvelle">{t("demandes.nouvelle")}</Link>
          </Bouton>
        ) : null}
      </Carte>
    </div>
  );
}
