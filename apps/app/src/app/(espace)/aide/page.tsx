import { Carte, EnTetePage } from "@emine/ui";
import type { Metadata } from "next";
import Link from "next/link";
import { getTranslations } from "next-intl/server";
import { chargerMoi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("aide");
  return { title: t("titre") };
}

/** Rubriques d'aide selon ce que fait le profil (aucune phrase qui ne le concerne pas). */
function rubriques(droits: Set<string>, interne: boolean): string[] {
  const liste: string[] = [];
  if (droits.has("DEPOSER")) liste.push("deposer", "payer", "suivre", "document");
  if (interne && (droits.has("INSTRUIRE") || droits.has("SIGNER"))) liste.push("traiter");
  if (droits.has("SIGNER")) liste.push("signer");
  if (droits.has("PARAMETRER") || droits.has("VALIDER_CATALOGUE")) liste.push("catalogue");
  liste.push("rechercher", "compte");
  return liste;
}

/**
 * Aide (lien en bas du menu, « Aide & mon compte » du cahier des charges) : réponses courtes aux
 * questions du profil connecté, raccourcis et contact de l'assistance.
 */
export default async function Aide() {
  const [moi, t] = await Promise.all([chargerMoi(), getTranslations("aide")]);
  const liste = rubriques(new Set(moi.permissions), moi.interne);
  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <EnTetePage titre={t("titre")} description={t("description")} />
      <Carte titre={t("questions")}>
        <div className="divide-y divide-bordure">
          {liste.map((cle) => (
            <details key={cle} className="group py-1">
              <summary className="flex min-h-11 cursor-pointer items-center font-semibold text-navy">
                {t(`rubriques.${cle}.question`)}
              </summary>
              <p className="pb-3 text-texte-doux">{t(`rubriques.${cle}.reponse`)}</p>
            </details>
          ))}
        </div>
      </Carte>
      <Carte titre={t("contact.titre")}>
        <p className="text-texte-doux">{t("contact.texte")}</p>
        <dl className="mt-3 flex flex-col gap-1">
          <div className="flex gap-2">
            <dt className="font-semibold text-navy">{t("contact.email")}</dt>
            <dd>
              <a
                href="mailto:assistance@e-mine.test"
                className="text-royal underline-offset-4 hover:underline"
              >
                assistance@e-mine.test
              </a>
            </dd>
          </div>
          <div className="flex gap-2">
            <dt className="font-semibold text-navy">{t("contact.horaires")}</dt>
            <dd>{t("contact.horairesValeur")}</dd>
          </div>
        </dl>
        <p className="mt-4 text-sm text-texte-doux">
          <Link href="/mentions-legales" className="text-royal underline-offset-4 hover:underline">
            {t("mentions")}
          </Link>
        </p>
      </Carte>
    </div>
  );
}
