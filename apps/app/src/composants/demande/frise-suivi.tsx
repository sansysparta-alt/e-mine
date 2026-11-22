import type { EtapeSuiviDto } from "@emine/api-client";
import { cn, formaterDate } from "@emine/ui";
import { Check, Minus, X } from "@emine/ui/icones";
import { useTranslations } from "next-intl";

export type FriseSuiviProps = {
  etapes: EtapeSuiviDto[];
  /** Profil interne : l'étape qui attend le demandeur se lit « En attente du demandeur ». */
  interne: boolean;
  /** Document à paiement unique (attestation) : « Paiement » au lieu de « Paiement des frais ». */
  paiementUnique?: boolean;
  /**
   * Ce qui débloque l'étape en cours, par clé d'étape (« Avis attendus : Agent DNM… »,
   * « Décision attendue : Ministre ») : chacun sait ce qu'on attend, et de qui.
   */
  precisions?: Record<string, string>;
};

/**
 * Frise de suivi (US-07.3) : 6 étapes visibles, calculées par l'API, dans l'ordre réel du circuit.
 * Même langage visuel que l'indicateur d'étapes du formulaire (pastille, coche, ligne de liaison) ;
 * l'état se lit en texte et en icône, jamais par la couleur seule ; l'étape en cours porte
 * `aria-current="step"`. Horizontale sur grand écran, verticale sur mobile.
 */
export function FriseSuivi({
  etapes,
  interne,
  paiementUnique = false,
  precisions = {},
}: FriseSuiviProps) {
  const t = useTranslations("suivi");
  if (etapes.length === 0) return null;
  return (
    <ol className="flex flex-col gap-0 md:grid md:grid-cols-6 md:gap-2" aria-label={t("titre")}>
      {etapes.map((e, i) => {
        const derniere = i === etapes.length - 1;
        const libelle =
          e.cle === "paiement-frais" && paiementUnique
            ? t("etapes.paiement")
            : t(`etapes.${e.cle}`);
        const attendDemandeur = e.etat === "en-cours" && e.actionDemandeur;
        return (
          <li
            key={e.cle}
            aria-current={e.etat === "en-cours" || e.etat === "rejetee" ? "step" : undefined}
            className="relative flex gap-3 pb-5 md:flex-col md:items-center md:pb-0 md:text-center"
          >
            {!derniere ? (
              <span
                aria-hidden="true"
                className={cn(
                  "absolute top-9 left-[1.0625rem] h-[calc(100%-2.25rem)] w-0.5 md:top-[1.0625rem] md:left-[calc(50%+1.25rem)] md:h-0.5 md:w-[calc(100%-2.5rem+0.5rem)]",
                  e.etat === "faite" ? "bg-royal" : "bg-bordure",
                )}
              />
            ) : null}
            <span
              aria-hidden="true"
              className={cn(
                "relative z-10 flex size-9 shrink-0 items-center justify-center rounded-full border-2 text-sm font-bold",
                e.etat === "faite" && "border-royal bg-royal text-white",
                e.etat === "en-cours" &&
                  (attendDemandeur
                    ? "border-avertissement bg-white text-avertissement ring-4 ring-ice"
                    : "border-royal bg-white text-royal ring-4 ring-ice"),
                e.etat === "rejetee" && "border-danger bg-danger text-white",
                (e.etat === "a-venir" || e.etat === "non-atteinte" || e.etat === "sans-objet") &&
                  "border-bordure bg-white text-texte-doux",
              )}
            >
              {e.etat === "faite" ? (
                <Check className="size-5" />
              ) : e.etat === "rejetee" ? (
                <X className="size-5" />
              ) : e.etat === "sans-objet" ? (
                <Minus className="size-5" />
              ) : (
                i + 1
              )}
            </span>
            <span className="flex flex-col pt-1.5 md:pt-0">
              <span
                className={cn(
                  "text-sm",
                  e.etat === "en-cours" || e.etat === "rejetee"
                    ? "font-bold text-navy"
                    : "font-semibold",
                  (e.etat === "a-venir" || e.etat === "non-atteinte" || e.etat === "sans-objet") &&
                    "font-normal text-texte-doux",
                )}
              >
                {libelle}
              </span>
              <span
                className={cn(
                  "text-sm",
                  attendDemandeur
                    ? "font-semibold text-avertissement"
                    : e.etat === "rejetee"
                      ? "font-semibold text-danger"
                      : "text-texte-doux",
                )}
              >
                {e.etat === "faite" && e.date
                  ? t("etats.faiteLe", { date: formaterDate(e.date) })
                  : e.etat === "rejetee" && e.date
                    ? t("etats.rejeteeLe", { date: formaterDate(e.date) })
                    : attendDemandeur
                      ? t(interne ? "etats.attenteDemandeur" : "etats.aVous")
                      : t(`etats.${e.etat}`)}
              </span>
              {e.etat === "en-cours" && precisions[e.cle] ? (
                <span className="text-sm text-texte-doux">{precisions[e.cle]}</span>
              ) : null}
            </span>
          </li>
        );
      })}
    </ol>
  );
}
