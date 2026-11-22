import type { PassagesDto } from "@emine/api-client";
import { Alerte, Carte, formaterDateHeure } from "@emine/ui";
import { useTranslations } from "next-intl";

/**
 * Passages du dossier dans le traitement en série (correctifs ergonomie), visibles de tous les
 * agents : qui, quand, à quelle étape, pourquoi ; alerte « Bloqué » au 3e passage à cette étape.
 */
export function HistoriquePassages({ historique }: { historique: PassagesDto }) {
  const t = useTranslations("travail.passages");
  const tStatut = useTranslations("statuts");
  if (historique.passages.length === 0) return null;
  return (
    <Carte titre={t("titre", { nombre: historique.passages.length })}>
      {historique.bloque ? (
        <Alerte
          nature="avertissement"
          titre={t("bloque", { nombre: historique.aLEtape })}
          className="mb-4"
        >
          {t("bloqueTexte")}
        </Alerte>
      ) : null}
      <ol className="flex flex-col divide-y divide-bordure">
        {historique.passages.map((p, i) => (
          <li key={`${p.le}-${i}`} className="flex flex-col gap-1 py-3 first:pt-0 last:pb-0">
            <p className="text-sm text-texte-doux">
              {t("ligne", {
                date: formaterDateHeure(p.le),
                auteur: p.auteur,
                etape: tStatut(p.statut),
              })}
            </p>
            <p className="whitespace-pre-line">{p.motif}</p>
          </li>
        ))}
      </ol>
    </Carte>
  );
}
