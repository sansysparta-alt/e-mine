"use client";

import type { DemandeDto, PieceDto } from "@emine/api-client";
import { Alerte, Bouton } from "@emine/ui";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useId, useState, useTransition } from "react";
import type { ResultatAction } from "@/serveur/erreurs-formulaire";

export type DepotDemandeProps = {
  demande: DemandeDto;
  piecesRequises: string[];
  pieces: PieceDto[];
  deposer: (id: number, declaration: boolean) => Promise<ResultatAction<DemandeDto>>;
};

/**
 * Fin du récapitulatif (US-03.4, correctif J2 B3) : pièces jointes, déclaration sur l'honneur,
 * dépôt. Le dépôt est bloqué tant qu'une pièce obligatoire du catalogue manque ; l'agent du
 * guichet juge ensuite la qualité des pièces.
 */
export function DepotDemande({ demande, piecesRequises, pieces, deposer }: DepotDemandeProps) {
  const t = useTranslations("demande.depot");
  const id = useId();
  const router = useRouter();
  const [declaration, setDeclaration] = useState(false);
  const [erreurs, setErreurs] = useState<string[]>([]);
  const [messageApi, setMessageApi] = useState("");
  const [manquantesApi, setManquantesApi] = useState<string[] | null>(null);
  const [enCours, demarrer] = useTransition();

  const fournies = new Set(pieces.map((p) => p.typePiece));
  const manquantesLocales = piecesRequises.filter((p) => !fournies.has(p));
  const manquantes = manquantesApi ?? manquantesLocales;

  function soumettre() {
    const locales = [
      ...(declaration ? [] : ["declarationHonneur"]),
      ...(manquantesLocales.length === 0 ? [] : ["pieces"]),
    ];
    setMessageApi("");
    setManquantesApi(null);
    setErreurs(locales);
    if (locales.length > 0) return;
    demarrer(async () => {
      const resultat = await deposer(demande.id, true);
      if (resultat.ok) {
        router.push(`/demandes/${demande.id}?deposee=1`);
        return;
      }
      setErreurs(resultat.erreur.manques?.length ? resultat.erreur.manques : ["autre"]);
      if (resultat.erreur.piecesManquantes?.length) {
        setManquantesApi(resultat.erreur.piecesManquantes);
      }
      setMessageApi(resultat.erreur.message);
    });
  }

  return (
    <div className="flex flex-col gap-4">
      <div>
        <p className="font-semibold">{t("piecesJointes", { nombre: pieces.length })}</p>
        {pieces.length > 0 ? (
          <ul className="list-disc pl-6">
            {pieces.map((p) => (
              <li key={p.id}>
                {p.typePiece} : {p.nomFichier}
              </li>
            ))}
          </ul>
        ) : null}
      </div>
      {manquantes.length > 0 ? (
        <Alerte nature="avertissement" titre={t("manquantesTitre")}>
          <ul className="list-disc pl-6">
            {manquantes.map((p) => (
              <li key={p}>{p}</li>
            ))}
          </ul>
          <p className="mt-1">{t("manquantes")}</p>
        </Alerte>
      ) : null}

      <div className="flex items-start gap-3">
        <input
          id={`${id}-declaration`}
          type="checkbox"
          checked={declaration}
          onChange={(e) => setDeclaration(e.target.checked)}
          className="mt-1 size-5 shrink-0"
          aria-invalid={erreurs.includes("declarationHonneur") ? true : undefined}
          aria-describedby={
            erreurs.includes("declarationHonneur") ? `${id}-erreur-declaration` : undefined
          }
        />
        <label htmlFor={`${id}-declaration`}>{t("declaration")}</label>
      </div>

      {erreurs.length > 0 ? (
        <Alerte nature="erreur" titre={t("impossible")}>
          <ul className="list-disc pl-6">
            {erreurs.map((e) => (
              <li key={e} id={e === "declarationHonneur" ? `${id}-erreur-declaration` : undefined}>
                {t(`manques.${["declarationHonneur", "pieces"].includes(e) ? e : "autre"}`)}
              </li>
            ))}
          </ul>
          {messageApi ? <p className="mt-2">{messageApi}</p> : null}
        </Alerte>
      ) : null}

      <div>
        <Bouton type="button" taille="grande" onClick={soumettre} disabled={enCours}>
          {enCours ? t("enCours") : t("deposer")}
        </Bouton>
      </div>
    </div>
  );
}
