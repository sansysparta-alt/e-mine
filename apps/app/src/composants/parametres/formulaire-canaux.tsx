"use client";

import type { CanauxDto, ChoixDto } from "@emine/api-client";
import { Alerte, Bouton, CaseACocher } from "@emine/ui";
import { useLocale, useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import type { ResultatAction } from "@/serveur/erreurs-formulaire";

const CANAUX = ["sms", "email", "interne"] as const;
type Canal = (typeof CANAUX)[number];

export type FormulaireCanauxProps = {
  initial: CanauxDto[];
  modifiable: boolean;
  enregistrer: (choix: ChoixDto[]) => Promise<ResultatAction<CanauxDto[]>>;
};

/**
 * Canaux des notifications (US-02.7) : pour chaque événement, SMS, e-mail et notification dans
 * l'application. Chaque SMS a un coût ; un événement garde au moins un canal pour que l'usager
 * sache toujours où en est son dossier.
 */
export function FormulaireCanaux({ initial, modifiable, enregistrer }: FormulaireCanauxProps) {
  const t = useTranslations("parametres.canaux");
  const langue = useLocale();
  const router = useRouter();
  const [lignes, setLignes] = useState(initial);
  const [erreur, setErreur] = useState<string | null>(null);
  const [succes, setSucces] = useState(false);
  const [enCours, demarrer] = useTransition();
  const sansCanal = lignes.filter((l) => !l.sms && !l.email && !l.interne);
  const smsActifs = lignes.filter((l) => l.sms).length;

  function basculer(modele: string, canal: Canal) {
    setSucces(false);
    setLignes((ls) => ls.map((l) => (l.modele === modele ? { ...l, [canal]: !l[canal] } : l)));
  }

  function envoyer(e: React.FormEvent) {
    e.preventDefault();
    setErreur(null);
    setSucces(false);
    if (sansCanal.length) return;
    demarrer(async () => {
      const r = await enregistrer(
        lignes.map((l) => ({ modele: l.modele, sms: l.sms, email: l.email, interne: l.interne })),
      );
      if (r.ok) {
        setLignes(r.valeur);
        setSucces(true);
        router.refresh();
      } else {
        setErreur(r.erreur.code === "INDISPONIBLE" ? t("indisponible") : r.erreur.message);
      }
    });
  }

  const libelle = (l: CanauxDto) => (langue === "en" ? l.libelleEn : l.libelleFr);

  return (
    <form onSubmit={envoyer} noValidate className="flex flex-col gap-4">
      <p className="text-sm text-texte-doux">
        {t("resume", { n: smsActifs, total: lignes.length })}
      </p>
      <div className="overflow-x-auto">
        <table className="w-full border-collapse text-left">
          <caption className="sr-only">{t("legende")}</caption>
          <thead>
            <tr className="border-b border-bordure text-sm text-texte-doux">
              <th scope="col" className="py-2 pr-4 font-semibold">
                {t("evenement")}
              </th>
              {CANAUX.map((c) => (
                <th key={c} scope="col" className="px-2 py-2 text-center font-semibold">
                  {t(`canal.${c}`)}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {lignes.map((l) => {
              const vide = !l.sms && !l.email && !l.interne;
              return (
                <tr key={l.modele} className="border-b border-bordure last:border-0">
                  <th scope="row" className="py-2 pr-4 font-normal">
                    <span className="font-semibold text-navy">{libelle(l)}</span>
                    {vide ? (
                      <span className="block text-sm font-semibold text-danger">
                        {t("auMoinsUn")}
                      </span>
                    ) : null}
                  </th>
                  {CANAUX.map((c) => (
                    <td key={c} className="px-2 py-1 text-center">
                      <CaseACocher
                        checked={l[c]}
                        disabled={!modifiable}
                        onChange={() => basculer(l.modele, c)}
                        className="justify-center"
                      >
                        <span className="sr-only">{`${t(`canal.${c}`)} — ${libelle(l)}`}</span>
                      </CaseACocher>
                    </td>
                  ))}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {succes ? (
        <Alerte nature="succes" titre={t("enregistre.titre")}>
          {t("enregistre.texte")}
        </Alerte>
      ) : null}
      {erreur ? (
        <Alerte nature="erreur" titre={t("echec")}>
          {erreur}
        </Alerte>
      ) : null}
      {modifiable ? (
        <div>
          <Bouton type="submit" disabled={enCours || sansCanal.length > 0}>
            {t("enregistrer")}
          </Bouton>
        </div>
      ) : null}
    </form>
  );
}
