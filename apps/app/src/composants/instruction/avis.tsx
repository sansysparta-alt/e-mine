"use client";

import type { EtatAvisDto, ResultatAvisDto } from "@emine/api-client";
import { Alerte, Bouton, cn, formaterDate } from "@emine/ui";
import { CircleCheck, CircleX, Clock, Save } from "@emine/ui/icones";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { libelleProfil } from "../referentiels";
import { useId, useState, useTransition } from "react";
import type { ResultatAction } from "@/serveur/erreurs-formulaire";
import { adresseApresAction, type ContexteSerie } from "../travail/file-travail";
import { ChampMotif, erreurMotif } from "./champ-motif";

/**
 * Avis techniques de la demande (US-05.3) : un par profil requis au catalogue, rendu (sens,
 * auteur, date, motivation) ou attendu. Icône + texte, jamais la couleur seule ; pas de badge (un
 * seul badge par ligne de liste, ici il s'agit d'un détail de fiche).
 */
export function ListeAvis({ avis }: { avis: EtatAvisDto }) {
  const t = useTranslations("instruction.avis");
  const tReferentiels = useTranslations("referentiels");
  return (
    <ul className="flex flex-col divide-y divide-bordure">
      {avis.requis.map((a) => {
        const Icone = !a.rendu ? Clock : a.favorable ? CircleCheck : CircleX;
        return (
          <li key={a.profil} className="flex flex-col gap-1 py-3 first:pt-0 last:pb-0">
            <p className="flex items-start gap-2">
              <Icone
                aria-hidden="true"
                className={cn(
                  "mt-0.5 size-5 shrink-0",
                  !a.rendu ? "text-texte-doux" : a.favorable ? "text-succes" : "text-danger",
                )}
              />
              <span>
                <span className="font-semibold">
                  {libelleProfil(a.profil, a.libelle, tReferentiels)}
                </span>
                {" · "}
                {!a.rendu ? t("attendu") : a.favorable ? t("favorable") : t("defavorable")}
              </span>
            </p>
            {a.rendu ? (
              <>
                <p className="pl-7 text-sm text-texte-doux">
                  {t("par", {
                    auteur: a.auteur ?? "",
                    date: a.renduLe ? formaterDate(a.renduLe) : "",
                  })}
                </p>
                <p className="pl-7 whitespace-pre-line">{a.motif}</p>
              </>
            ) : null}
          </li>
        );
      })}
    </ul>
  );
}

export type RendreAvisProps = {
  demandeId: number;
  numero: string;
  rendre: (
    id: number,
    favorable: boolean,
    motif: string,
  ) => Promise<ResultatAction<ResultatAvisDto>>;
  serie?: ContexteSerie | null;
};

/**
 * Avis motivé du profil désigné par le catalogue (US-05.3) : sens (favorable ou défavorable, choix
 * explicite, rien de présélectionné) et motivation obligatoire. L'avis éclaire la décision du
 * signataire, il ne décide pas. Après l'enregistrement, le dossier suivant de la file s'ouvre.
 */
export function RendreAvis({ demandeId, numero, rendre, serie }: RendreAvisProps) {
  const t = useTranslations("instruction.avis.rendre");
  const id = useId();
  const router = useRouter();
  const [sens, setSens] = useState<"favorable" | "defavorable" | null>(null);
  const [erreurSens, setErreurSens] = useState(false);
  const [motif, setMotif] = useState("");
  const [erreurChampMotif, setErreurChampMotif] = useState<string | undefined>();
  const [erreurApi, setErreurApi] = useState<string | null>(null);
  const [enCours, demarrer] = useTransition();

  function enregistrer() {
    const probleme = erreurMotif(motif);
    setErreurSens(sens === null);
    if (probleme) setErreurChampMotif(t(`erreurs.${probleme}`));
    if (sens === null || probleme) return;
    setErreurApi(null);
    demarrer(async () => {
      const r = await rendre(demandeId, sens === "favorable", motif.trim());
      if (r.ok) {
        router.push(
          adresseApresAction(serie ?? null, "avis-a-rendre", {
            numero,
            resultat: r.valeur.statut === "avis_rendu" ? "avis_rendu" : "avis",
          }),
        );
        return;
      }
      const champ = r.erreur.champs.find((c) => c.champ === "motif");
      if (champ) {
        setErreurChampMotif(champ.message);
        return;
      }
      setErreurApi(r.erreur.message);
      router.refresh();
    });
  }

  const options = [
    { valeur: "favorable" as const, Icone: CircleCheck },
    { valeur: "defavorable" as const, Icone: CircleX },
  ];

  return (
    <div className="flex flex-col gap-4">
      <fieldset aria-describedby={erreurSens ? `${id}-sens-erreur` : undefined}>
        <legend className="font-semibold">
          {t("sens")}{" "}
          <span aria-hidden="true" className="text-danger">
            *
          </span>
        </legend>
        <div className="mt-2 grid grid-cols-1 gap-2 sm:grid-cols-2">
          {options.map(({ valeur, Icone }) => (
            <label
              key={valeur}
              className={cn(
                "flex min-h-11 cursor-pointer items-center gap-2 rounded-lg border px-3 py-2",
                sens === valeur ? "border-royal bg-ice" : "border-bordure bg-white",
              )}
            >
              <input
                type="radio"
                name={`${id}-sens`}
                value={valeur}
                checked={sens === valeur}
                onChange={() => {
                  setSens(valeur);
                  setErreurSens(false);
                }}
                className="size-5 accent-royal"
              />
              <Icone aria-hidden="true" className="size-5" />
              {t(valeur)}
            </label>
          ))}
        </div>
        {erreurSens ? (
          <p id={`${id}-sens-erreur`} className="mt-1 text-sm font-semibold text-danger">
            {t("sensRequis")}
          </p>
        ) : null}
      </fieldset>
      <ChampMotif
        id={`${id}-motif`}
        libelle={t("motif")}
        aide={t("motifAide")}
        valeur={motif}
        surChangement={(v) => {
          setMotif(v);
          if (erreurChampMotif && !erreurMotif(v)) setErreurChampMotif(undefined);
        }}
        erreur={erreurChampMotif}
      />
      {erreurApi ? (
        <Alerte nature="erreur" titre={t("echec")}>
          {erreurApi}
        </Alerte>
      ) : null}
      <div>
        <Bouton type="button" taille="grande" onClick={enregistrer} disabled={enCours}>
          <Save aria-hidden="true" className="size-5" />
          {enCours ? t("enCours") : t("enregistrer")}
        </Bouton>
      </div>
    </div>
  );
}
