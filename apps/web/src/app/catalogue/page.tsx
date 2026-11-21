import { listerTypesDocuments, type TypeDocumentDto } from "@emine/api-client";
import { Alerte } from "@emine/ui";
import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { ListeCatalogue } from "@/composants/liste-catalogue";

// Rendu serveur à chaque requête : le catalogue vient de l'API (jamais d'appel au moment du build).
export const dynamic = "force-dynamic";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("catalogue");
  return { title: t("titre") };
}

async function charger(): Promise<TypeDocumentDto[] | null> {
  try {
    return (await listerTypesDocuments()).data;
  } catch (e) {
    console.error("Catalogue : API injoignable", e);
    return null;
  }
}

export default async function PageCatalogue() {
  const t = await getTranslations("catalogue");
  const documents = await charger();
  return (
    <main id="contenu" className="mx-auto flex max-w-5xl flex-col gap-6 px-4 py-10 sm:px-6">
      <h1 className="text-3xl sm:text-4xl">{t("titre")}</h1>
      <p className="max-w-3xl text-lg text-texte-doux">{t("presentation")}</p>
      {documents ? (
        <ListeCatalogue documents={documents} />
      ) : (
        <Alerte nature="erreur" titre={t("indisponible.titre")}>
          {t("indisponible.conseil")}
        </Alerte>
      )}
    </main>
  );
}
