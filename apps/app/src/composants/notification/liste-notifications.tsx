import type { NotificationDto } from "@emine/api-client";
import { classesLienEtire, classesLigneCliquable, cn, formaterDateHeure } from "@emine/ui";
import Link from "next/link";
import { useTranslations } from "next-intl";

/** Adresse d'ouverture : marque la notification comme lue puis ouvre le dossier concerné. */
export function adresseOuverture(n: Pick<NotificationDto, "id" | "demandeId">): string {
  return n.demandeId == null
    ? `/notifications/${n.id}/ouvrir`
    : `/notifications/${n.id}/ouvrir?demande=${n.demandeId}`;
}

/** Premier paragraphe du message : la ligne reste courte, le dossier donne le détail. */
export function resume(texte: string): string {
  return texte.split(/\n\s*\n/)[0]?.trim() ?? "";
}

/**
 * Liste des notifications (US-07.2) : la ligne entière est un vrai lien vers le dossier (Tab,
 * Entrée, Ctrl+clic). Une notification non lue se repère par un titre en gras, une pastille et le
 * mot « Non lue » lu par les lecteurs d'écran (jamais la couleur seule).
 */
export function ListeNotifications({ notifications }: { notifications: NotificationDto[] }) {
  const t = useTranslations("notifications");
  return (
    <ul className="divide-y divide-bordure overflow-hidden rounded-carte border border-bordure bg-white">
      {notifications.map((n) => (
        <li key={n.id} className={cn(classesLigneCliquable, "flex gap-3 px-4 py-4 sm:px-5")}>
          <span
            aria-hidden="true"
            className={cn(
              "mt-2 size-2.5 shrink-0 rounded-full",
              n.lue ? "bg-transparent" : "bg-royal",
            )}
          />
          <div className="flex min-w-0 flex-1 flex-col gap-1 sm:flex-row sm:items-start sm:gap-6">
            <div className="min-w-0 flex-1">
              <Link
                href={adresseOuverture(n)}
                aria-label={n.lue ? undefined : `${t("nonLue")} : ${n.titre}`}
                className={cn(
                  classesLienEtire,
                  "text-navy",
                  n.lue ? "font-normal" : "font-semibold",
                )}
              >
                {n.titre}
              </Link>
              <p className="mt-1 line-clamp-2 text-sm text-texte-doux">{resume(n.texte)}</p>
            </div>
            <p className="shrink-0 text-sm whitespace-nowrap text-texte-doux">
              {formaterDateHeure(n.creeLe)}
            </p>
          </div>
        </li>
      ))}
    </ul>
  );
}
