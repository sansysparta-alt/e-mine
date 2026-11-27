"use client";

import { Alerte, Bouton, cn } from "@emine/ui";
import { FilePen } from "@emine/ui/icones";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useId, useState, useTransition } from "react";
import type { ResultatAction } from "@/serveur/erreurs-formulaire";
import { ChampDateEffet, aujourdhuiConakry, dateEffetValide } from "../gouvernance/gouvernance";
import { ChampMotif, erreurMotif } from "../instruction/champ-motif";

/** La règle en une phrase (règle en vigueur, proposition, historique). */
export function ResumeRegleRemboursement({ rembourserSiRejet }: { rembourserSiRejet: boolean }) {
  const t = useTranslations("parametres.remboursement");
  return (
    <div className="flex flex-col gap-1">
      <p className="font-semibold">{t(rembourserSiRejet ? "oui" : "non")}</p>
      <p className="text-sm text-texte-doux">{t("toujours")}</p>
    </div>
  );
}

/**
 * Proposition d'une nouvelle règle de remboursement des frais (décision V1-4), par
 * l'administrateur : le choix contraire à la règle en vigueur, une date d'effet et un motif. Rien
 * ne change avant la validation du Ministre.
 */
export function FormulaireRegleRemboursement({
  enVigueur,
  proposer,
}: {
  enVigueur: boolean;
  proposer: (
    rembourserSiRejet: boolean,
    dateEffet: string,
    motif: string,
  ) => Promise<ResultatAction<{ version: number }>>;
}) {
  const t = useTranslations("parametres.remboursement");
  const tMotif = useTranslations("instruction.motif");
  const tDate = useTranslations("gouvernance.champDate");
  const id = useId();
  const router = useRouter();
  const [ouvert, setOuvert] = useState(false);
  const [dateEffet, setDateEffet] = useState(aujourdhuiConakry());
  const [motif, setMotif] = useState("");
  const [erreurs, setErreurs] = useState<{ date?: string; motif?: string; api?: string }>({});
  const [enCours, demarrer] = useTransition();

  if (!ouvert) {
    return (
      <div>
        <Bouton type="button" variante="secondaire" onClick={() => setOuvert(true)}>
          <FilePen aria-hidden="true" className="size-5" />
          {t("proposer")}
        </Bouton>
      </div>
    );
  }

  function envoyer(e: React.FormEvent) {
    e.preventDefault();
    const probleme = erreurMotif(motif);
    const dateKo = !dateEffetValide(dateEffet);
    if (probleme || dateKo) {
      setErreurs({
        motif: probleme ? tMotif(`erreurs.${probleme}`) : undefined,
        date: dateKo ? tDate("erreur") : undefined,
      });
      return;
    }
    demarrer(async () => {
      const r = await proposer(!enVigueur, dateEffet, motif);
      if (!r.ok) {
        setErreurs({ api: r.erreur.message });
        return;
      }
      router.push(`/parametres?proposee=${r.valeur.version}`);
      router.refresh();
    });
  }

  return (
    <form
      onSubmit={envoyer}
      className="flex flex-col gap-4 border-t border-bordure pt-4"
      noValidate
    >
      <h3 className="font-semibold text-navy">{t("proposer")}</h3>
      <p className={cn("rounded-lg bg-ice p-3")}>
        {t("nouvelle")} <strong>{t(!enVigueur ? "oui" : "non")}</strong>
      </p>
      <ChampDateEffet
        id={`${id}-date`}
        valeur={dateEffet}
        surChangement={setDateEffet}
        erreur={erreurs.date}
      />
      <ChampMotif
        id={`${id}-motif`}
        libelle={t("motif")}
        aide={t("motifAide")}
        valeur={motif}
        surChangement={setMotif}
        erreur={erreurs.motif}
      />
      {erreurs.api ? (
        <Alerte nature="erreur" titre={t("erreur")}>
          {erreurs.api}
        </Alerte>
      ) : null}
      <div className="flex flex-wrap gap-3">
        <Bouton type="submit" disabled={enCours}>
          {enCours ? t("enCours") : t("envoyer")}
        </Bouton>
        <Bouton
          type="button"
          variante="secondaire"
          disabled={enCours}
          onClick={() => setOuvert(false)}
        >
          {t("annuler")}
        </Bouton>
      </div>
    </form>
  );
}
