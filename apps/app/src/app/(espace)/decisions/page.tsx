import { redirect } from "next/navigation";

type Props = { searchParams: Promise<Record<string, string | undefined>> };

/**
 * Ancienne page « Décisions et signatures » (US-05.4, US-06.3) : remplacée par la page unique
 * « À traiter » (correctifs J5). Les liens existants mènent au groupe correspondant.
 */
export default async function AnciennesDecisions({ searchParams }: Props) {
  const p = await searchParams;
  const q = new URLSearchParams({ groupe: p.onglet === "a-signer" ? "a-signer" : "a-decider" });
  for (const cle of ["q", "traitee", "resultat", "document"]) {
    const valeur = p[cle];
    if (valeur) q.set(cle, valeur);
  }
  redirect(`/a-traiter?${q.toString()}`);
}
