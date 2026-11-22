import { cn } from "@emine/ui";
import { Check } from "@emine/ui/icones";

export type EtatEtape = "faite" | "courante" | "a-venir";

export type EtapeIndicateur = {
  cle: string;
  libelle: string;
  etat: EtatEtape;
  /** L'étape peut être ouverte (faite ou déjà atteinte) ; une étape non atteinte n'est pas cliquable. */
  accessible: boolean;
};

export type IndicateurEtapesProps = {
  titre: string;
  etapes: EtapeIndicateur[];
  /** Libellés pour les lecteurs d'écran : « étape faite », « étape en cours », « à venir ». */
  libellesEtat: Record<EtatEtape, string>;
  surChoix: (cle: string) => void;
  desactive?: boolean;
};

/**
 * Indicateur de progression d'un formulaire en plusieurs étapes (correctifs ergonomie) : pastille
 * numérotée (coche une fois faite), ligne de liaison, trois états distincts (faite, en cours, à
 * venir). Seules les étapes atteintes sont des boutons ; une étape à venir n'a pas l'air cliquable.
 */
export function IndicateurEtapes({
  titre,
  etapes,
  libellesEtat,
  surChoix,
  desactive = false,
}: IndicateurEtapesProps) {
  return (
    <nav aria-label={titre}>
      <ol className="flex items-start">
        {etapes.map((e, i) => {
          const contenu = (
            <>
              <span
                aria-hidden="true"
                className={cn(
                  "flex size-9 shrink-0 items-center justify-center rounded-full border-2 text-sm font-bold",
                  e.etat === "faite" && "border-royal bg-royal text-white",
                  e.etat === "courante" && "border-royal bg-white text-royal ring-4 ring-ice",
                  e.etat === "a-venir" && "border-bordure bg-white text-texte-doux",
                )}
              >
                {e.etat === "faite" ? <Check className="size-5" /> : i + 1}
              </span>
              <span
                className={cn(
                  "text-center text-sm",
                  e.etat === "courante" ? "font-bold text-navy" : "font-semibold",
                  e.etat === "a-venir" && "font-normal text-texte-doux",
                )}
              >
                {e.libelle}
                <span className="sr-only">{` (${libellesEtat[e.etat]})`}</span>
              </span>
            </>
          );
          return (
            <li
              key={e.cle}
              aria-current={e.etat === "courante" ? "step" : undefined}
              className="relative flex flex-1 flex-col items-center"
            >
              {i > 0 ? (
                <span
                  aria-hidden="true"
                  className={cn(
                    "absolute top-[18px] h-0.5",
                    e.etat === "a-venir" ? "bg-bordure" : "bg-royal",
                  )}
                  style={{ right: "calc(50% + 18px)", width: "calc(100% - 36px)" }}
                />
              ) : null}
              {e.accessible && e.etat !== "courante" ? (
                <button
                  type="button"
                  onClick={() => surChoix(e.cle)}
                  disabled={desactive}
                  className="relative z-10 flex min-h-11 flex-col items-center gap-1 rounded-lg px-2 hover:text-royal disabled:opacity-60"
                >
                  {contenu}
                </button>
              ) : (
                <span className="relative z-10 flex min-h-11 flex-col items-center gap-1 px-2">
                  {contenu}
                </span>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
