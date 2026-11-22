"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import { useId } from "react";
import { classesChamp } from "../champ";
import { cn } from "../cn";
import { formaterNombre } from "../format";
import { TAILLES_PAGE } from "../taille-page";
import type { ComposantLien } from "./liste";

export type LibellesPagination = {
  /** « Pagination » : nom de la zone de navigation. */
  zone: string;
  afficher: string;
  parPage: string;
  /** Résumé, par exemple « 1–10 sur 36 demandes » ; reçoit début, fin, total déjà formatés. */
  resume: (debut: string, fin: string, total: string) => string;
  precedente: string;
  suivante: string;
  page: (numero: number) => string;
  /** Version courte sur mobile : « Page 2 sur 4 ». */
  pageSur: (numero: number, total: number) => string;
};

export type PaginationProps = {
  /** Adresse de la liste, sans paramètres de pagination. */
  chemin: string;
  /** Paramètres courants à conserver (filtres, recherche, tri). */
  parametres?: Record<string, string | undefined>;
  page: number;
  taille: number;
  total: number;
  tailles?: number[];
  libelles: LibellesPagination;
  langue?: string;
  Lien?: ComposantLien;
};

const LienParDefaut: ComposantLien = ({ children, ...props }) => <a {...props}>{children}</a>;

/**
 * Numéros de page affichés : la première, la dernière, la courante et ses voisines ; « … » pour
 * les sauts. Exemple pour la page 6 sur 12 : 1 … 5 6 7 … 12.
 */
export function pagesAffichees(page: number, totalPages: number): (number | "…")[] {
  if (totalPages <= 7) return Array.from({ length: totalPages }, (_, i) => i + 1);
  const garder = new Set([1, totalPages, page - 1, page, page + 1]);
  if (page <= 3) [2, 3, 4].forEach((p) => garder.add(p));
  if (page >= totalPages - 2)
    [totalPages - 3, totalPages - 2, totalPages - 1].forEach((p) => garder.add(p));
  const pages = [...garder].filter((p) => p >= 1 && p <= totalPages).sort((a, b) => a - b);
  const resultat: (number | "…")[] = [];
  pages.forEach((p, i) => {
    if (i > 0 && p - (pages[i - 1] as number) > 1) resultat.push("…");
    resultat.push(p);
  });
  return resultat;
}

/** Adresse d'une page, en conservant les autres paramètres. */
export function adressePage(
  chemin: string,
  parametres: Record<string, string | undefined>,
  page: number,
  taille: number,
): string {
  const q = new URLSearchParams();
  for (const [cle, valeur] of Object.entries(parametres)) {
    if (valeur) q.set(cle, valeur);
  }
  if (page > 1) q.set("page", String(page));
  q.set("taille", String(taille));
  return `${chemin}?${q.toString()}`;
}

/**
 * Pagination commune à toutes les listes (socle) : choix de la taille de page, résumé
 * « 1–10 sur 36 », pages précédente/suivante et numéros (abrégés par « … ») ; sur mobile, « Page 2
 * sur 4 » avec précédente/suivante. Page courante annoncée (`aria-current`), cibles de 44 px.
 */
export function Pagination({
  chemin,
  parametres = {},
  page,
  taille,
  total,
  tailles = [...TAILLES_PAGE],
  libelles,
  langue = "fr",
  Lien = LienParDefaut,
}: PaginationProps) {
  const id = useId();
  const totalPages = Math.max(1, Math.ceil(total / taille));
  const debut = total === 0 ? 0 : (page - 1) * taille + 1;
  const fin = Math.min(page * taille, total);
  const adresse = (p: number, t = taille) => adressePage(chemin, parametres, p, t);
  const bouton =
    "inline-flex min-h-11 min-w-11 items-center justify-center gap-1 rounded-lg border px-3 font-semibold";

  return (
    <div className="flex flex-col gap-3 border-t border-bordure pt-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-texte-doux">
        <form method="get" action={chemin} className="flex items-center gap-2">
          {Object.entries(parametres).map(([cle, v]) =>
            v ? <input key={cle} type="hidden" name={cle} value={v} /> : null,
          )}
          <label htmlFor={`${id}-taille`}>{libelles.afficher}</label>
          <select
            id={`${id}-taille`}
            name="taille"
            defaultValue={String(taille)}
            onChange={(e) => e.currentTarget.form?.requestSubmit()}
            className={cn(classesChamp, "w-auto py-1")}
          >
            {tailles.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </select>
          <span>{libelles.parPage}</span>
          <noscript>
            <button type="submit" className={cn(bouton, "border-bordure bg-white text-royal")}>
              OK
            </button>
          </noscript>
        </form>
        <p role="status">
          {libelles.resume(
            formaterNombre(debut, langue),
            formaterNombre(fin, langue),
            formaterNombre(total, langue),
          )}
        </p>
      </div>

      {totalPages > 1 ? (
        <nav aria-label={libelles.zone}>
          <ul className="flex items-center gap-1.5">
            <li>
              {page > 1 ? (
                <Lien
                  href={adresse(page - 1)}
                  className={cn(bouton, "border-bordure bg-white text-royal hover:border-royal")}
                >
                  <ChevronLeft aria-hidden="true" className="size-4" />
                  {libelles.precedente}
                </Lien>
              ) : (
                <span
                  aria-disabled="true"
                  className={cn(bouton, "border-bordure bg-fond text-texte-doux opacity-60")}
                >
                  <ChevronLeft aria-hidden="true" className="size-4" />
                  {libelles.precedente}
                </span>
              )}
            </li>
            <li className="px-2 font-semibold text-navy sm:hidden">
              {libelles.pageSur(page, totalPages)}
            </li>
            {pagesAffichees(page, totalPages).map((p, i) => (
              <li key={`${p}-${i}`} className="hidden sm:block">
                {p === "…" ? (
                  <span
                    aria-hidden="true"
                    className="inline-flex min-h-11 min-w-8 items-center justify-center text-texte-doux"
                  >
                    …
                  </span>
                ) : (
                  <Lien
                    href={adresse(p)}
                    aria-current={p === page ? "page" : undefined}
                    aria-label={libelles.page(p)}
                    className={cn(
                      bouton,
                      p === page
                        ? "border-navy bg-navy text-white"
                        : "border-bordure bg-white text-navy hover:border-royal",
                    )}
                  >
                    {p}
                  </Lien>
                )}
              </li>
            ))}
            <li>
              {page < totalPages ? (
                <Lien
                  href={adresse(page + 1)}
                  className={cn(bouton, "border-bordure bg-white text-royal hover:border-royal")}
                >
                  {libelles.suivante}
                  <ChevronRight aria-hidden="true" className="size-4" />
                </Lien>
              ) : (
                <span
                  aria-disabled="true"
                  className={cn(bouton, "border-bordure bg-fond text-texte-doux opacity-60")}
                >
                  {libelles.suivante}
                  <ChevronRight aria-hidden="true" className="size-4" />
                </span>
              )}
            </li>
          </ul>
        </nav>
      ) : null}
    </div>
  );
}
