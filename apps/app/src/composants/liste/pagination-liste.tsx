"use client";

import { type ComposantLien, Pagination } from "@emine/ui";
import Link from "next/link";
import { useTranslations } from "next-intl";

const LienNext: ComposantLien = ({ href, children, ...props }) => (
  <Link href={href} {...props}>
    {children}
  </Link>
);

export type PaginationListeProps = {
  chemin: string;
  parametres?: Record<string, string | undefined>;
  page: number;
  taille: number;
  total: number;
  /**
   * Nom des éléments dans le résumé, au singulier et au pluriel (« 1 demande », « 1–20 sur 45
   * demandes ») ; une seule chaîne garde l'ancien comportement (pluriel seul).
   */
  elements: string | { un: string; plusieurs: string };
};

/** Pagination du socle avec les libellés de l'application et la navigation Next. */
export function PaginationListe({ elements, ...props }: PaginationListeProps) {
  const t = useTranslations("pagination");
  return (
    <Pagination
      {...props}
      Lien={LienNext}
      libelles={{
        zone: t("zone"),
        afficher: t("afficher"),
        parPage: t("parPage"),
        resume: (debut, fin, total) =>
          typeof elements === "string"
            ? t("resume", { debut, fin, total, elements })
            : total === "1"
              ? t("resumeUn", { element: elements.un })
              : t("resume", { debut, fin, total, elements: elements.plusieurs }),
        precedente: t("precedente"),
        suivante: t("suivante"),
        page: (numero) => t("page", { numero }),
        pageSur: (numero, total) => t("pageSur", { numero, total }),
      }}
    />
  );
}
