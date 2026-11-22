import { redirect } from "next/navigation";

type Props = { searchParams: Promise<Record<string, string | undefined>> };

/**
 * Ancienne « File du guichet » (US-05.1) : remplacée par la page unique « À traiter » (correctifs
 * J5). Les liens et favoris existants mènent au groupe « À examiner », paramètres conservés.
 */
export default async function AncienneFileGuichet({ searchParams }: Props) {
  const p = await searchParams;
  const q = new URLSearchParams({
    groupe: p.onglet === "frais-attendus" ? "frais-attendus" : "a-examiner",
  });
  for (const cle of ["q", "traitee", "resultat"]) {
    const valeur = p[cle];
    if (valeur) q.set(cle, valeur);
  }
  redirect(`/a-traiter?${q.toString()}`);
}
