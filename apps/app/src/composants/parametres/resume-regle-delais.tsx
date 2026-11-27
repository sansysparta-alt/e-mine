import type { ParametresDelaiDto } from "@emine/api-client";
import { useTranslations } from "next-intl";
import { ChangementsProposes, type LigneChangement } from "../gouvernance/gouvernance";

type Traduire = ReturnType<typeof useTranslations<"parametres.delais">>;

function pausesEnClair(regle: ParametresDelaiDto, t: Traduire): string[] {
  return [
    regle.pauseComplement ? t("pauses.complement") : null,
    regle.pauseDroits ? t("pauses.droits") : null,
  ].filter((p): p is string => p !== null);
}

/** Les trois critères de la règle des délais, en clair (résumé et comparaison). */
function criteres(regle: ParametresDelaiDto, t: Traduire): LigneChangement[] {
  const pauses = pausesEnClair(regle, t);
  const valeurs = [
    [t("resume.depart"), t(`depart.${regle.depart}`)],
    [t("resume.pauses"), pauses.length === 0 ? t("resume.aucunePause") : pauses.join(" ; ")],
    [
      t("resume.seuil"),
      t("resume.seuilValeur", { pct: regle.seuilProchePourcent, min: regle.seuilProcheMinJours }),
    ],
  ];
  return valeurs.map(([libelle, valeur]) => ({
    libelle: libelle as string,
    avant: valeur as string,
    apres: valeur as string,
  }));
}

/** Règle des délais en clair (consultation, proposition à valider, historique). */
export function ResumeRegleDelais({ regle }: { regle: ParametresDelaiDto }) {
  const t = useTranslations("parametres.delais");
  const pauses = pausesEnClair(regle, t);
  return (
    <dl className="grid grid-cols-1 gap-x-6 gap-y-2 sm:grid-cols-[auto_1fr]">
      <dt className="font-semibold text-navy">{t("resume.depart")}</dt>
      <dd>{t(`depart.${regle.depart}`)}</dd>
      <dt className="font-semibold text-navy">{t("resume.pauses")}</dt>
      <dd>
        {pauses.length === 0 ? (
          t("resume.aucunePause")
        ) : (
          <ul className="flex flex-col">
            {pauses.map((p) => (
              <li key={p}>{p}</li>
            ))}
          </ul>
        )}
      </dd>
      <dt className="font-semibold text-navy">{t("resume.seuil")}</dt>
      <dd>
        {t("resume.seuilValeur", {
          pct: regle.seuilProchePourcent,
          min: regle.seuilProcheMinJours,
        })}
      </dd>
    </dl>
  );
}

/**
 * Changements « de … à … » entre deux règles des délais (US-02.10) : formulaire de proposition
 * (comparaison à la dernière règle validée) et proposition lue par le Ministre (comparaison à la
 * règle en vigueur).
 */
export function ChangementsRegleDelais({
  avant,
  apres,
  formulaire = false,
}: {
  avant: ParametresDelaiDto;
  apres: ParametresDelaiDto;
  formulaire?: boolean;
}) {
  const t = useTranslations("parametres.delais");
  const a = criteres(avant, t);
  const b = criteres(apres, t);
  return (
    <ChangementsProposes
      formulaire={formulaire}
      lignes={a.map((l, i) => ({ libelle: l.libelle, avant: l.avant, apres: b[i]!.apres }))}
    />
  );
}
