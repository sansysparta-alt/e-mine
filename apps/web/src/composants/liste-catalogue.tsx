import type { TypeDocumentDto } from "@emine/api-client";
import { Carte, formaterGnf } from "@emine/ui";
import { useTranslations } from "next-intl";

export type ListeCatalogueProps = {
  documents: TypeDocumentDto[];
};

/** Regroupe les documents par famille, dans l'ordre reçu de l'API. */
export function parFamille(documents: TypeDocumentDto[]) {
  const familles = new Map<string, { libelle: string; documents: TypeDocumentDto[] }>();
  for (const d of documents) {
    const famille = familles.get(d.famille.code) ?? { libelle: d.famille.libelle, documents: [] };
    famille.documents.push(d);
    familles.set(d.famille.code, famille);
  }
  return [...familles.entries()].map(([code, f]) => ({ code, ...f }));
}

/**
 * Catalogue public des documents (US-02.1) : par famille, chaque document avec son délai, ses
 * pièces requises, son signataire et ses tarifs.
 */
export function ListeCatalogue({ documents }: ListeCatalogueProps) {
  const t = useTranslations("catalogue");
  return (
    <div className="flex flex-col gap-10">
      {parFamille(documents).map((famille) => (
        <section key={famille.code} aria-labelledby={`famille-${famille.code}`}>
          <h2 id={`famille-${famille.code}`} className="mb-4 text-2xl">
            {famille.libelle}
          </h2>
          <ul className="grid gap-4 md:grid-cols-2">
            {famille.documents.map((d) => (
              <li key={d.code}>
                <Carte className="h-full">
                  <h3 className="text-lg">
                    {d.libelleFr} <span className="text-texte-doux">({d.code})</span>
                  </h3>
                  <dl className="mt-3 grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-base">
                    <dt className="font-semibold">{t("delai")}</dt>
                    <dd>{t("jours", { nombre: d.delaiJours })}</dd>
                    <dt className="font-semibold">{t("signataire")}</dt>
                    <dd>{d.signataire}</dd>
                    {d.tarifs.fraisDossierGnf != null ? (
                      <>
                        <dt className="font-semibold">{t("fraisDossier")}</dt>
                        <dd>{formaterGnf(d.tarifs.fraisDossierGnf)}</dd>
                      </>
                    ) : null}
                    {d.tarifs.droitsGnf != null ? (
                      <>
                        <dt className="font-semibold">{t("droits")}</dt>
                        <dd>{formaterGnf(d.tarifs.droitsGnf)}</dd>
                      </>
                    ) : null}
                    {d.tarifs.attestationGnf != null ? (
                      <>
                        <dt className="font-semibold">{t("paiementUnique")}</dt>
                        <dd>{formaterGnf(d.tarifs.attestationGnf)}</dd>
                      </>
                    ) : null}
                  </dl>
                  <p className="mt-3 font-semibold">{t("pieces")}</p>
                  <ul className="mt-1 list-disc pl-6">
                    {d.piecesRequises.map((p) => (
                      <li key={p}>{p}</li>
                    ))}
                  </ul>
                </Carte>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}
