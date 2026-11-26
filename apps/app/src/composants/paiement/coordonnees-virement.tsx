import { BoutonCopier, Carte, formaterGnf } from "@emine/ui";
import { useTranslations } from "next-intl";

/**
 * Coordonnées du virement (US-04.7) : compte à créditer, montant exact et référence à indiquer
 * dans le libellé, chacun copiable d'un geste. Valeurs données par l'API (configuration).
 */
export function CoordonneesVirement({
  coordonnees,
  montantGnf,
  reference,
}: {
  coordonnees: { beneficiaire: string; banque: string; iban: string; bic: string };
  montantGnf: number;
  reference: string;
}) {
  const t = useTranslations("detailPaiement.coordonnees");
  const tCopier = useTranslations("commun.copier");
  const montant = formaterGnf(montantGnf);
  const lignes: { cle: string; valeur: string; copiable: boolean; fort?: boolean }[] = [
    { cle: "beneficiaire", valeur: coordonnees.beneficiaire, copiable: false },
    { cle: "banque", valeur: coordonnees.banque, copiable: false },
    { cle: "iban", valeur: coordonnees.iban, copiable: true },
    { cle: "bic", valeur: coordonnees.bic, copiable: true },
    { cle: "montant", valeur: montant, copiable: false, fort: true },
    { cle: "libelle", valeur: reference, copiable: true, fort: true },
  ];
  return (
    <Carte titre={t("titre")}>
      <dl className="grid grid-cols-1 gap-x-6 gap-y-1 sm:grid-cols-[auto_1fr]">
        {lignes.map((l) => (
          <div key={l.cle} className="contents">
            <dt className="pt-2 font-semibold sm:pt-2.5">{t(l.cle)}</dt>
            <dd className="flex min-h-11 flex-wrap items-center gap-1 break-all">
              <span
                className={
                  l.fort ? "font-semibold text-navy tabular-nums" : "tabular-nums select-all"
                }
              >
                {l.valeur}
              </span>
              {l.copiable ? (
                <BoutonCopier
                  valeur={l.cle === "iban" ? l.valeur.replace(/\s/g, "") : l.valeur}
                  libelle={tCopier("libelle", { quoi: t(l.cle) })}
                  libelleCopie={tCopier("fait", { quoi: t(l.cle) })}
                />
              ) : null}
            </dd>
          </div>
        ))}
      </dl>
      <p className="mt-4 text-sm text-texte-doux">{t("aide")}</p>
    </Carte>
  );
}
