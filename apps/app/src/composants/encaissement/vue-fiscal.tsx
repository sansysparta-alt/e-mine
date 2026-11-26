import type { IndicateursEncaissementsDto } from "@emine/api-client";
import { Bouton, CarteChiffre, formaterDate, formaterGnf, GrilleChiffres } from "@emine/ui";
import { Banknote, FileSpreadsheet, ReceiptText, SearchCheck, Undo2 } from "@emine/ui/icones";
import Link from "next/link";
import { useTranslations } from "next-intl";

/**
 * Tableau de bord du Responsable fiscal (US-04.11) : ce qu'il doit faire à ce jour (à vérifier,
 * remboursements à valider, ordres à enregistrer) et les recettes du mois. Chaque carte ouvre la
 * liste qui donne le même nombre ; chiffres calculés par l'API.
 */
export function VueFiscal({ indicateurs: i }: { indicateurs: IndicateursEncaissementsDto }) {
  const t = useTranslations("tableauDeBord.fiscal");
  const tPeriodes = useTranslations("tableauDeBord.periodes");
  const aCeJour = tPeriodes("aCeJour");
  return (
    <div className="flex flex-col gap-6">
      <section aria-labelledby="fiscal-a-faire" className="flex flex-col gap-4">
        <h2 id="fiscal-a-faire" className="text-xl font-semibold">
          {t("aFaire")}
        </h2>
        <GrilleChiffres colonnes={4}>
          <CarteChiffre
            libelle={t("aVerifier")}
            periode={aCeJour}
            valeur={i.aVerifier}
            precision={t("aVerifierPrecision")}
            icone={SearchCheck}
            href="/a-verifier"
          />
          <CarteChiffre
            libelle={t("remboursementsAValider")}
            periode={aCeJour}
            valeur={i.remboursementsAValider}
            precision={t("remboursementsAValiderPrecision")}
            icone={Undo2}
            href="/remboursements?statut=demande"
          />
          <CarteChiffre
            libelle={t("ordresAEnregistrer")}
            periode={aCeJour}
            valeur={i.ordresAEnregistrer}
            precision={t("ordresAEnregistrerPrecision")}
            icone={ReceiptText}
            href="/remboursements?statut=valide"
          />
          <CarteChiffre
            libelle={t("recettesMois")}
            periode={t("ceMois", { du: formaterDate(i.debutMois), au: formaterDate(i.finMois) })}
            valeur={formaterGnf(i.mois.soldeGnf)}
            precision={t("recettesMoisPrecision", { nombre: i.mois.nombre })}
            icone={Banknote}
            href={`/journal-encaissements?du=${i.debutMois}&au=${i.finMois}`}
          />
        </GrilleChiffres>
      </section>
      <div className="flex flex-wrap gap-3">
        <Bouton asChild variante="secondaire">
          <Link href="/releves">
            <FileSpreadsheet aria-hidden="true" className="size-5" />
            {t("importer")}
          </Link>
        </Bouton>
      </div>
    </div>
  );
}
