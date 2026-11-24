import type { NotificationDto } from "@emine/api-client";
import {
  classesAuDessusDuLien,
  classesLienEtire,
  classesLigneCliquable,
  cn,
  formaterDate,
  formaterGnf,
  formaterHeure,
  joursCalendairesDepuis,
} from "@emine/ui";
import { CreditCard, FilePlus2 } from "@emine/ui/icones";
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

/** Jour d'une notification, à l'heure de Conakry : « aujourd'hui », « hier » ou sa date. */
export type JourNotification = { cle: string; notifications: NotificationDto[] };

/** Regroupe des notifications (déjà triées, des plus récentes aux plus anciennes) par jour. */
export function parJour(
  notifications: NotificationDto[],
  maintenant = new Date(),
): JourNotification[] {
  const jours: JourNotification[] = [];
  for (const n of notifications) {
    const ecart = joursCalendairesDepuis(n.creeLe, maintenant);
    const cle = ecart === 0 ? "aujourdhui" : ecart === 1 ? "hier" : formaterDate(n.creeLe);
    const dernier = jours.at(-1);
    if (dernier?.cle === cle) dernier.notifications.push(n);
    else jours.push({ cle, notifications: [n] });
  }
  return jours;
}

const classesAction =
  "inline-flex min-h-11 items-center gap-1 rounded-lg border-2 border-royal bg-white px-3 text-sm font-semibold text-royal hover:bg-ice";

/**
 * Liste des notifications (US-07.2), **groupées par jour** (Aujourd'hui, Hier, puis la date) : la
 * ligne entière est un vrai lien vers le dossier (Tab, Entrée, Ctrl+clic). Une notification non lue
 * se repère par un titre en gras, une pastille et le mot « Non lue » lu par les lecteurs d'écran
 * (jamais la couleur seule). Quand une action est encore attendue (payer les droits, compléter),
 * la ligne donne le montant et le bouton de l'action, au-dessus du lien de la ligne.
 */
export function ListeNotifications({
  notifications,
  maintenant,
}: {
  notifications: NotificationDto[];
  /** Date de référence (tests). */
  maintenant?: Date;
}) {
  const t = useTranslations("notifications");
  return (
    <div className="flex flex-col gap-6">
      {parJour(notifications, maintenant).map((jour) => (
        <section
          key={jour.cle}
          aria-labelledby={`jour-${jour.cle}`}
          className="flex flex-col gap-2"
        >
          <h2 id={`jour-${jour.cle}`} className="text-base font-semibold text-texte-doux">
            {jour.cle === "aujourdhui"
              ? t("jours.aujourdhui")
              : jour.cle === "hier"
                ? t("jours.hier")
                : jour.cle}
          </h2>
          <ul className="divide-y divide-bordure overflow-hidden rounded-carte border border-bordure bg-white">
            {jour.notifications.map((n) => (
              <li key={n.id} className={cn(classesLigneCliquable, "flex gap-3 px-4 py-4 sm:px-5")}>
                <span
                  aria-hidden="true"
                  className={cn(
                    "mt-2 size-2.5 shrink-0 rounded-full",
                    n.lue ? "bg-transparent" : "bg-royal",
                  )}
                />
                <div className="flex min-w-0 flex-1 flex-col gap-1 sm:flex-row sm:items-start sm:gap-6">
                  <div className="flex min-w-0 flex-1 flex-col gap-2">
                    <div>
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
                    {n.action && n.demandeId != null ? (
                      <div className="flex flex-wrap items-center gap-3">
                        {n.action === "payer-droits" && n.montantGnf != null ? (
                          <span className="font-semibold text-navy tabular-nums">
                            {t("montantDroits", { montant: formaterGnf(n.montantGnf) })}
                          </span>
                        ) : null}
                        <Link
                          href={
                            n.action === "payer-droits"
                              ? `/demandes/${n.demandeId}/paiement`
                              : `/demandes/${n.demandeId}`
                          }
                          className={cn(classesAuDessusDuLien, classesAction)}
                        >
                          {n.action === "payer-droits" ? (
                            <CreditCard aria-hidden="true" className="size-4" />
                          ) : (
                            <FilePlus2 aria-hidden="true" className="size-4" />
                          )}
                          {t(
                            n.action === "payer-droits"
                              ? "actions.payerDroits"
                              : "actions.completer",
                          )}
                          {n.numeroDemande ? (
                            <span className="sr-only"> · {n.numeroDemande}</span>
                          ) : null}
                        </Link>
                      </div>
                    ) : null}
                  </div>
                  <p className="shrink-0 text-sm whitespace-nowrap text-texte-doux">
                    {formaterHeure(n.creeLe)}
                  </p>
                </div>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}
