import type { DemandeDto } from "@emine/api-client";
import { Bouton, Carte } from "@emine/ui";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { ListeDemandes } from "./demande/liste-demandes";

/** Nombre de demandes récentes affichées sur le tableau de bord. */
export const DEMANDES_RECENTES = 5;

export type VueTableauDeBordProps = {
  prenom: string;
  nom: string;
  profilLibelle: string;
  interne: boolean;
  /** Droit « déposer » (US-01.6) : affiche le bouton de nouvelle demande. */
  peutDeposer?: boolean;
  /** Demandes de l'usager, la plus récente d'abord (US-03.5). */
  demandes?: DemandeDto[];
  /** Libellé de chaque document du catalogue, par code. */
  libelles?: Record<string, string>;
};

/**
 * Tableau de bord : salutation au nom de l'utilisateur, profil, et le bloc principal adapté au type
 * de profil (demandes récentes pour un usager, dossiers à traiter pour un agent).
 */
export function VueTableauDeBord({
  prenom,
  nom,
  profilLibelle,
  interne,
  peutDeposer = false,
  demandes = [],
  libelles = {},
}: VueTableauDeBordProps) {
  const t = useTranslations("tableauDeBord");
  const bloc = interne ? "dossiers" : "demandes";
  const avecDemandes = !interne && demandes.length > 0;
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-3xl sm:text-4xl">{t("bonjour", { prenom, nom })}</h1>
        <p className="mt-2 text-lg text-texte-doux">{t("profil", { libelle: profilLibelle })}</p>
      </div>
      <Carte titre={t(`${bloc}.titre`)}>
        {avecDemandes ? (
          <>
            <ListeDemandes demandes={demandes.slice(0, DEMANDES_RECENTES)} libelles={libelles} />
            <p className="mt-3">
              <Link
                href="/demandes"
                className="inline-flex min-h-11 items-center text-royal underline"
              >
                {t("demandes.toutes", { nombre: demandes.length })}
              </Link>
            </p>
          </>
        ) : (
          <>
            <p className="font-semibold">{t(`${bloc}.vide`)}</p>
            <p className="mt-1 text-texte-doux">{t(`${bloc}.aide`)}</p>
          </>
        )}
        {peutDeposer ? (
          <Bouton asChild className="mt-4">
            <Link href="/demandes/nouvelle">{t("demandes.nouvelle")}</Link>
          </Bouton>
        ) : null}
      </Carte>
    </div>
  );
}
