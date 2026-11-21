"use client";

import type { MoyenDto, PaiementDto } from "@emine/api-client";
import { Alerte, Bouton, cn } from "@emine/ui";
import { Building2, CreditCard } from "@emine/ui/icones";
import { useTranslations } from "next-intl";
import { useId, useState, useTransition } from "react";
import type { ResultatAction } from "@/serveur/erreurs-formulaire";

type Moyen = "TRESORPAY" | "VIREMENT";

export type ChoixMoyenProps = {
  demandeId: number;
  moyens: MoyenDto[];
  /** Montant déjà formaté (« 5 000 000 GNF »), repris sur le bouton. */
  montant: string;
  /** Paiements en ligne encore possibles aujourd'hui (3 par jour, US-04.3). */
  tentativesRestantes: number;
  /** Action serveur : crée le paiement puis redirige vers TrésorPay (US-04.3). */
  choisir: (demandeId: number, moyen: Moyen) => Promise<ResultatAction<PaiementDto>>;
};

const ICONES = { TRESORPAY: CreditCard, VIREMENT: Building2 } as const;

/**
 * Choix du moyen de paiement (US-04.2) : aucune présélection ; un moyen indisponible reste visible,
 * désactivé, avec la raison. Chaque option est une carte entière cliquable (bouton radio natif,
 * clavier et lecteurs d'écran). Le bouton reprend le montant et le moyen choisi.
 */
export function ChoixMoyen({
  demandeId,
  moyens,
  montant,
  tentativesRestantes,
  choisir,
}: ChoixMoyenProps) {
  const t = useTranslations("paiement.choix");
  const id = useId();
  const [moyen, setMoyen] = useState<Moyen | null>(null);
  const [erreurSaisie, setErreurSaisie] = useState(false);
  const [erreurApi, setErreurApi] = useState<{ code: string; message: string } | null>(null);
  const [cree, setCree] = useState<PaiementDto | null>(null);
  const [enCours, demarrer] = useTransition();
  const epuisees = tentativesRestantes <= 0;

  function valider() {
    setErreurApi(null);
    if (!moyen) {
      setErreurSaisie(true);
      document.getElementById(`${id}-TRESORPAY`)?.focus();
      return;
    }
    demarrer(async () => {
      const resultat = await choisir(demandeId, moyen);
      if (!resultat.ok) {
        setErreurApi({ code: resultat.erreur.code, message: resultat.erreur.message });
        return;
      }
      setCree(resultat.valeur);
    });
  }

  return (
    <div className="flex flex-col gap-4">
      <fieldset
        aria-describedby={erreurSaisie ? `${id}-erreur` : undefined}
        aria-invalid={erreurSaisie || undefined}
      >
        <legend className="mb-3 font-semibold">{t("legende")}</legend>
        <div className="grid gap-3 sm:grid-cols-2">
          {moyens.map((m) => {
            const code = m.moyen as Moyen;
            const Icone = ICONES[code] ?? CreditCard;
            const choisi = moyen === code;
            return (
              <label
                key={code}
                htmlFor={`${id}-${code}`}
                className={cn(
                  "relative flex min-h-11 items-start gap-3 rounded-lg border-2 p-4 transition-colors",
                  m.disponible
                    ? "cursor-pointer bg-white hover:border-royal"
                    : "cursor-not-allowed border-dashed bg-fond text-texte-doux",
                  choisi ? "border-royal bg-ice" : "border-bordure",
                  erreurSaisie && m.disponible && !choisi ? "border-danger" : "",
                )}
              >
                <input
                  id={`${id}-${code}`}
                  type="radio"
                  name={`${id}-moyen`}
                  value={code}
                  checked={choisi}
                  disabled={!m.disponible || enCours}
                  aria-describedby={`${id}-${code}-aide`}
                  onChange={() => {
                    setMoyen(code);
                    setErreurSaisie(false);
                  }}
                  className="mt-1 size-5 shrink-0 cursor-pointer accent-royal disabled:cursor-not-allowed"
                />
                <span className="flex flex-col gap-1">
                  <span className="flex flex-wrap items-center gap-2 font-semibold text-texte">
                    <Icone aria-hidden="true" className="size-5 text-royal" />
                    {t(`moyens.${code}.titre`)}
                    {!m.disponible ? (
                      <span className="rounded-full bg-white px-2 py-0.5 text-sm font-normal text-texte-doux ring-1 ring-bordure">
                        {t("bientot")}
                      </span>
                    ) : null}
                  </span>
                  <span id={`${id}-${code}-aide`} className="text-sm text-texte-doux">
                    {t(`moyens.${code}.aide`)}
                  </span>
                </span>
              </label>
            );
          })}
        </div>
        {erreurSaisie ? (
          <p id={`${id}-erreur`} className="mt-2 font-semibold text-danger">
            {t("obligatoire")}
          </p>
        ) : null}
      </fieldset>

      {erreurApi ? (
        <Alerte
          nature={erreurApi.code === "PAY-003" ? "avertissement" : "erreur"}
          titre={t(`erreurs.${titreErreur(erreurApi.code)}`)}
        >
          {erreurApi.code === "PAY-003"
            ? t("indisponibleTexte")
            : erreurApi.message || t("erreurs.reessayer")}
        </Alerte>
      ) : null}

      {epuisees ? (
        <Alerte nature="avertissement" titre={t("erreurs.PAY-002")}>
          {t("tentativesEpuisees")}
        </Alerte>
      ) : null}

      {cree ? (
        <Alerte nature="succes" titre={t("cree", { reference: cree.reference })}>
          {t("creeSuite")}
        </Alerte>
      ) : null}

      <div className="flex flex-col gap-2">
        <Bouton
          type="button"
          taille="grande"
          onClick={valider}
          disabled={enCours || epuisees}
          className="self-start"
        >
          {enCours
            ? t("enCours")
            : moyen
              ? t(`payerAvec.${moyen}`, { montant })
              : t("continuer", { montant })}
        </Bouton>
        {!epuisees ? (
          <p className="text-sm text-texte-doux">
            {t("redirection")} {t("tentatives", { nombre: tentativesRestantes })}
          </p>
        ) : null}
      </div>
    </div>
  );
}

/** Titre d'erreur connu, ou générique. */
function titreErreur(code: string): string {
  return ["PAY-001", "PAY-002", "PAY-003", "PAY-007"].includes(code) ? code : "autre";
}
