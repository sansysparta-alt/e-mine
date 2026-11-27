"use client";

import type { ElementAVerifierDto, PaiementAssociableDto } from "@emine/api-client";
import {
  Alerte,
  Bouton,
  classesAuDessusDuLien,
  cn,
  Dialogue,
  formaterDate,
  formaterGnf,
} from "@emine/ui";
import { Archive, Ban, CircleCheck, Coins, Link2, Undo2 } from "@emine/ui/icones";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useId, useState, useTransition } from "react";
import type { DecisionEcart } from "@/serveur/actions-encaissements";
import type { ResultatAction } from "@/serveur/erreurs-formulaire";
import { ChampMotif, erreurMotif } from "../instruction/champ-motif";

const ICONES = {
  VALIDER: CircleCheck,
  REJETER: Ban,
  COMPLETER: Coins,
  ASSOCIER: Link2,
  REMBOURSER: Undo2,
  CLASSER: Archive,
} as const satisfies Record<DecisionEcart, unknown>;

/**
 * Le motif est obligatoire pour rejeter ou demander le complément (communiqués au demandeur), ou
 * classer.
 */
export function motifObligatoire(decision: DecisionEcart): boolean {
  return decision === "REJETER" || decision === "CLASSER" || decision === "COMPLETER";
}

export type ChargerAssociables = (
  ligneId: number,
) => Promise<ResultatAction<PaiementAssociableDto[]>>;

export type DeciderEcart = (
  cible: { ligneId: number } | { reference: string },
  decision: DecisionEcart,
  motif: string,
  reference: string,
) => Promise<ResultatAction<null>>;

/**
 * Décisions du Responsable fiscal sur un élément « À vérifier » (US-04.9) : seules celles que
 * l'API propose sont affichées. Chacune se confirme dans une boîte de dialogue qui en rappelle
 * l'effet ; le motif (obligatoire pour rejeter ou classer) et la référence à associer y sont
 * saisis. La décision est tracée par l'API.
 */
export function DecisionsEcart({
  element,
  decider,
  chargerAssociables,
}: {
  element: ElementAVerifierDto;
  decider: DeciderEcart;
  /** Virements auxquels associer une ligne (R4) : choisis dans une liste, jamais saisis. */
  chargerAssociables?: ChargerAssociables;
}) {
  const t = useTranslations("aVerifier.decision");
  const tMotif = useTranslations("instruction.motif");
  const id = useId();
  const router = useRouter();
  const [choix, setChoix] = useState<DecisionEcart | null>(null);
  const [motif, setMotif] = useState("");
  const [reference, setReference] = useState("");
  const [erreurs, setErreurs] = useState<{
    motif?: string;
    reference?: string;
    api?: string;
  }>({});
  const [enCours, demarrer] = useTransition();
  const [associables, setAssociables] = useState<PaiementAssociableDto[] | null>(null);
  const decisions = element.decisions as DecisionEcart[];

  function ouvrir(d: DecisionEcart) {
    setChoix(d);
    setMotif("");
    setReference("");
    setErreurs({});
    setAssociables(null);
    if (d === "ASSOCIER" && element.ligneId != null && chargerAssociables) {
      void chargerAssociables(element.ligneId).then((r) => {
        if (r.ok) {
          setAssociables(r.valeur);
          if (r.valeur.length === 1) setReference(r.valeur[0]?.reference ?? "");
        } else {
          setAssociables([]);
          setErreurs({ api: r.erreur.message });
        }
      });
    }
  }

  function envoyer() {
    if (!choix) return;
    const probleme = motifObligatoire(choix) ? erreurMotif(motif) : null;
    const refInvalide = choix === "ASSOCIER" && reference === "";
    if (probleme || refInvalide) {
      setErreurs({
        motif: probleme ? tMotif(`erreurs.${probleme}`) : undefined,
        reference: refInvalide ? t("referenceInvalide") : undefined,
      });
      return;
    }
    demarrer(async () => {
      const cible =
        element.type === "ligne" && element.ligneId != null
          ? { ligneId: element.ligneId }
          : { reference: element.referencePaiement ?? "" };
      const r = await decider(cible, choix, motif, reference);
      if (!r.ok) {
        const champMotif = r.erreur.champs.find((c) => c.champ === "motif");
        const champRef = r.erreur.champs.find((c) => c.champ === "reference");
        setErreurs({
          motif: champMotif?.message,
          reference: champRef?.message,
          api: champMotif || champRef ? undefined : r.erreur.message,
        });
        return;
      }
      setChoix(null);
      router.refresh();
    });
  }

  if (decisions.length === 0) return null;
  return (
    <div className={cn(classesAuDessusDuLien, "flex flex-wrap gap-2")}>
      {decisions.map((d) => {
        const Icone = ICONES[d];
        return (
          <Bouton
            key={d}
            type="button"
            variante={
              d === "VALIDER" || d === "ASSOCIER" || d === "COMPLETER" ? "primaire" : "secondaire"
            }
            taille="ligne"
            onClick={() => ouvrir(d)}
          >
            <Icone aria-hidden="true" className="size-4" />
            {t(`${d}.bouton`)}
            <span className="sr-only">
              {" "}
              · {element.referencePaiement ?? element.libelle ?? element.ligneId}
            </span>
          </Bouton>
        );
      })}
      <Dialogue
        ouvert={choix !== null}
        titre={choix ? t(`${choix}.titre`) : ""}
        surFermeture={enCours ? undefined : () => setChoix(null)}
        actions={
          <>
            <Bouton
              type="button"
              variante="secondaire"
              disabled={enCours}
              onClick={() => setChoix(null)}
            >
              {t("annuler")}
            </Bouton>
            <Bouton
              type="button"
              disabled={enCours || (choix === "ASSOCIER" && !associables?.length)}
              onClick={envoyer}
            >
              {enCours ? t("enCours") : choix ? t(`${choix}.confirmer`) : ""}
            </Bouton>
          </>
        }
      >
        {choix ? (
          <div className="flex flex-col gap-4">
            <p>
              {t(`${choix}.effet`, {
                reste: formaterGnf(
                  Math.max(0, (element.montantAttenduGnf ?? 0) - element.montantGnf),
                ),
              })}
            </p>
            {choix === "ASSOCIER" ? (
              <fieldset
                className="flex flex-col gap-2"
                aria-describedby={erreurs.reference ? `${id}-reference-erreur` : undefined}
              >
                <legend className="font-semibold">{t("reference")}</legend>
                <p className="text-sm text-texte-doux">
                  {t("referenceAide", { montant: formaterGnf(element.montantGnf) })}
                </p>
                {associables === null ? (
                  <p role="status" className="text-texte-doux">
                    {t("chargement")}
                  </p>
                ) : associables.length === 0 ? (
                  <Alerte nature="info" titre={t("aucunAssociable.titre")}>
                    {t("aucunAssociable.texte")}
                  </Alerte>
                ) : (
                  <ul className="flex flex-col gap-2">
                    {associables.map((a) => (
                      <li key={a.reference}>
                        <label
                          className={cn(
                            "flex min-h-11 cursor-pointer items-start gap-3 rounded-lg border-2 px-3 py-2",
                            reference === a.reference
                              ? "border-royal bg-ice"
                              : "border-bordure bg-white",
                          )}
                        >
                          <input
                            type="radio"
                            name={`${id}-reference`}
                            value={a.reference}
                            checked={reference === a.reference}
                            onChange={() => setReference(a.reference)}
                            className="mt-1 size-5 shrink-0 accent-royal"
                          />
                          <span className="flex flex-col">
                            <span className="font-semibold whitespace-nowrap tabular-nums">
                              {a.reference}
                              {a.numeroDemande ? (
                                <span className="font-normal text-texte-doux">
                                  {" "}
                                  · {a.numeroDemande}
                                </span>
                              ) : null}
                            </span>
                            <span className="text-sm text-texte-doux">
                              {a.declareLe
                                ? t("declareLe", { date: formaterDate(a.declareLe) })
                                : t("nonDeclare")}
                            </span>
                          </span>
                        </label>
                      </li>
                    ))}
                  </ul>
                )}
                {erreurs.reference ? (
                  <p id={`${id}-reference-erreur`} className="font-semibold text-danger">
                    {erreurs.reference}
                  </p>
                ) : null}
              </fieldset>
            ) : null}
            <ChampMotif
              id={`${id}-motif`}
              libelle={t("motif")}
              aide={
                choix === "REJETER"
                  ? t("motifAideRejet")
                  : choix === "COMPLETER"
                    ? t("motifAideComplement")
                    : t("motifAide")
              }
              valeur={motif}
              surChangement={setMotif}
              erreur={erreurs.motif}
              obligatoire={motifObligatoire(choix)}
              apercu={
                choix === "REJETER" || choix === "COMPLETER"
                  ? t("apercu.demandeur")
                  : t("apercu.journal")
              }
            />
            {erreurs.api ? (
              <Alerte nature="erreur" titre={t("erreur")}>
                {erreurs.api}
              </Alerte>
            ) : null}
          </div>
        ) : null}
      </Dialogue>
    </div>
  );
}
