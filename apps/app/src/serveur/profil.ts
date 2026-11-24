import type { MoiDto } from "@emine/api-client";
import { getTranslations } from "next-intl/server";
import { libelleProfil } from "@/composants/referentiels";

/** Nom du profil de l'utilisateur connecté, dans la langue de l'interface. */
export async function profilAffiche(moi: Pick<MoiDto, "profil">): Promise<string> {
  const t = await getTranslations("referentiels");
  return libelleProfil(moi.profil.code, moi.profil.libelle, t);
}
