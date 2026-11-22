import { Bouton, classesChamp } from "@emine/ui";
import { ShieldCheck } from "@emine/ui/icones";
import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { extraireCode } from "@/composants/verification";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("verification");
  return { title: t("titre") };
}

type Props = { searchParams: Promise<{ code?: string }> };

/**
 * Vérifier un document sans QR (US-06.5) : on saisit le code imprimé sous le QR, ou on colle
 * l'adresse lue par le téléphone. Fonctionne sans JavaScript (formulaire GET).
 */
export default async function SaisieVerification({ searchParams }: Props) {
  const { code } = await searchParams;
  const extrait = extraireCode(code);
  if (extrait) redirect(`/verifier/${extrait}`);
  const t = await getTranslations("verification");
  const erreur = code !== undefined && code.trim() !== "";

  return (
    <main id="contenu" className="mx-auto flex max-w-3xl flex-col gap-6 px-4 py-10 sm:px-6">
      <h1 className="text-3xl sm:text-4xl">{t("titre")}</h1>
      <p className="max-w-2xl text-lg text-texte-doux">{t("saisie.presentation")}</p>
      <form method="get" action="/verifier" className="flex flex-col gap-2" noValidate>
        <label htmlFor="code" className="font-semibold">
          {t("saisie.libelle")}
        </label>
        <p id="code-aide" className="text-sm text-texte-doux">
          {t("saisie.aide")}
        </p>
        <div className="flex flex-col gap-3 sm:flex-row">
          <input
            id="code"
            name="code"
            type="text"
            inputMode="text"
            autoComplete="off"
            spellCheck={false}
            defaultValue={code}
            aria-invalid={erreur ? true : undefined}
            aria-describedby={erreur ? "code-aide code-erreur" : "code-aide"}
            className={classesChamp}
          />
          <Bouton type="submit" className="shrink-0">
            <ShieldCheck aria-hidden="true" className="size-5" />
            {t("saisie.bouton")}
          </Bouton>
        </div>
        {erreur ? (
          <p id="code-erreur" className="font-semibold text-danger">
            {t("saisie.erreur")}
          </p>
        ) : null}
      </form>
      <p className="text-sm text-texte-doux">{t("confidentialite")}</p>
    </main>
  );
}
