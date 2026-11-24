import Link from "next/link";
import { getTranslations } from "next-intl/server";
import paquet from "../../../package.json";

/**
 * Pied de page de l'espace connecté (socle) : mentions légales, aide et version de l'application,
 * discrets, sur chaque écran.
 */
export async function PiedDePage() {
  const t = await getTranslations("piedDePage");
  const lien = "inline-flex min-h-11 items-center text-royal underline-offset-4 hover:underline";
  return (
    <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
      <p>{t("ministere")}</p>
      <nav aria-label={t("libelle")}>
        <ul className="flex flex-wrap items-center gap-x-5">
          <li>
            <Link href="/aide" className={lien}>
              {t("aide")}
            </Link>
          </li>
          <li>
            <Link href="/mentions-legales" className={lien}>
              {t("mentionsLegales")}
            </Link>
          </li>
          <li>{t("version", { version: paquet.version })}</li>
        </ul>
      </nav>
    </div>
  );
}
