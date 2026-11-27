"use client";

import { accorder, type ComposantLien, type FormesAccord, Pagination } from "@emine/ui";
import Link from "next/link";
import { useLocale, useTranslations } from "next-intl";

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
  /** Nom des éléments du résumé, accordé avec le total : « 1 demande », « 36 demandes ». */
  elements: FormesAccord;
};

/** Pagination du socle avec les libellés de l'application et la navigation Next. */
export function PaginationListe({ elements, ...props }: PaginationListeProps) {
  const t = useTranslations("pagination");
  const langue = useLocale();
  return (
    <Pagination
      {...props}
      Lien={LienNext}
      libelles={{
        zone: t("zone"),
        afficher: t("afficher"),
        parPage: t("parPage"),
        resume: (debut, fin, total) =>
          t("resume", { debut, fin, total, elements: accorder(props.total, elements, langue) }),
        precedente: t("precedente"),
        suivante: t("suivante"),
        page: (numero) => t("page", { numero }),
        pageSur: (numero, total) => t("pageSur", { numero, total }),
      }}
    />
  );
}
