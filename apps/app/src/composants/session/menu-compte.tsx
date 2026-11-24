"use client";

import { type ComposantLien, MenuUtilisateur } from "@emine/ui";
import { LogOut, UserRound } from "@emine/ui/icones";
import { useRouter } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { useRef, useTransition } from "react";
import { LANGUES } from "@/i18n/langues";
import { changerLangue } from "@/serveur/actions-langue";
import { effacerToutesLesSaisies } from "../demande/sauvegarde-locale";

/** Nom de chaque langue dans sa propre langue (règle d'accessibilité : attribut `lang`). */
const NOMS_LANGUES: Record<(typeof LANGUES)[number], string> = { fr: "Français", en: "English" };

/**
 * Menu sous l'avatar : « Mon profil », langue de l'interface (bilingue FR / EN du cahier des
 * charges), puis « Se déconnecter ». La déconnexion reste un envoi POST (jamais un simple lien) et
 * efface d'abord les saisies gardées dans le navigateur, pour qu'un poste partagé ne conserve
 * rien du compte qui part.
 */
export function MenuCompte({
  utilisateur,
  Lien,
}: {
  utilisateur: { nomComplet: string; profil: string };
  Lien: ComposantLien;
}) {
  const t = useTranslations("navigation.compte");
  const langue = useLocale();
  const router = useRouter();
  const [, demarrer] = useTransition();
  const deconnexion = useRef<HTMLFormElement>(null);

  return (
    <MenuUtilisateur
      nomComplet={utilisateur.nomComplet}
      profil={utilisateur.profil}
      libelle={t("menu")}
      Lien={Lien}
      elements={[
        { genre: "lien", cle: "profil", libelle: t("profil"), href: "/profil", icone: UserRound },
        {
          genre: "groupe",
          cle: "langue",
          libelle: t("langue"),
          choix: LANGUES.map((l) => ({
            cle: l,
            libelle: NOMS_LANGUES[l],
            lang: l,
            coche: langue === l,
            surSelection: () =>
              demarrer(async () => {
                await changerLangue(l);
                router.refresh();
              }),
          })),
        },
        { genre: "separateur", cle: "separateur" },
        {
          genre: "action",
          cle: "deconnexion",
          libelle: t("deconnexion"),
          icone: LogOut,
          surSelection: () => {
            effacerToutesLesSaisies();
            deconnexion.current?.requestSubmit();
          },
        },
      ]}
      enPlus={
        <form ref={deconnexion} action="/api/auth/logout" method="post" hidden aria-hidden="true" />
      }
    />
  );
}
