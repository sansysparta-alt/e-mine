import type { EntreeJournalDto, TableauDto } from "@emine/api-client";
import {
  BadgeStatut,
  Carte,
  CarteChiffre,
  type ColonneTableau,
  type ComposantLien,
  formaterDateHeure,
  GrilleChiffres,
  TableauDonnees,
} from "@emine/ui";
import { Activity, ScrollText, ShieldCheck, Users } from "@emine/ui/icones";
import Link from "next/link";
import { useLocale, useTranslations } from "next-intl";
import { libelleObjet } from "../audit/journal";
import { libelleProfil } from "../referentiels";

const LienNext: ComposantLien = ({ href, children, ...props }) => (
  <Link href={href} {...props}>
    {children}
  </Link>
);

type ProfilLigne = TableauDto["profils"][number];

/**
 * Vue d'ensemble de l'administrateur technique (cahier des charges : « Utilisateurs actifs,
 * Profils configurés, Disponibilité », « Utilisateurs & rôles », journal d'audit). Chaque carte
 * ouvre la liste qui donne le même nombre ; chiffres et états calculés par l'API.
 */
export function VueAdministration({
  tableau,
  operations,
}: {
  tableau: TableauDto;
  /** Dernières opérations du journal d'audit. */
  operations: EntreeJournalDto[];
}) {
  const t = useTranslations("administration");
  const tReferentiels = useTranslations("referentiels");
  const tAudit = useTranslations("audit");
  const anglais = useLocale() === "en";
  /** « Demande déposée », « DEM-2026-000012 », ou « Tarif #3 » (le type d'objet en contexte). */
  const objetOperation = (o: EntreeJournalDto) => {
    const l = libelleObjet(o, anglais, tAudit);
    return l.startsWith("#") && tAudit.has(`tables.${o.table}`)
      ? `${tAudit(`tables.${o.table}`)} ${l}`
      : l;
  };
  const aCeJour = t("periodes.aCeJour");
  const configures = tableau.profils.filter((p) => p.droits > 0).length;
  const disponibles = tableau.services.filter((s) => s.disponible).length;
  const colonnes: ColonneTableau<ProfilLigne>[] = [
    {
      cle: "profil",
      entete: t("profils.colonnes.profil"),
      rendu: (p) => (
        <span className="flex flex-col">
          <span className="font-semibold text-navy">
            {libelleProfil(p.code, p.libelle, tReferentiels)}
          </span>
          <span className="text-sm text-texte-doux">
            {p.interne ? t("profils.interne") : t("profils.externe")}
          </span>
        </span>
      ),
    },
    {
      cle: "utilisateurs",
      entete: t("profils.colonnes.utilisateurs"),
      rendu: (p) => <span className="tabular-nums">{p.utilisateurs}</span>,
    },
    {
      cle: "droits",
      entete: t("profils.colonnes.droits"),
      rendu: (p) =>
        p.droits > 0 ? (
          <span className="tabular-nums">{p.droits}</span>
        ) : (
          <BadgeStatut ton="avertissement">{t("profils.sansDroit")}</BadgeStatut>
        ),
    },
  ];

  return (
    <div className="flex flex-col gap-6">
      <GrilleChiffres>
        <CarteChiffre
          libelle={t("chiffres.actifs")}
          periode={aCeJour}
          valeur={tableau.comptes.actifs}
          precision={t("chiffres.actifsPrecision", {
            suspendus: tableau.comptes.suspendus,
            invites: tableau.comptes.invites,
          })}
          icone={Users}
          href="/utilisateurs?statut=actif"
        />
        <CarteChiffre
          libelle={t("chiffres.connectes")}
          periode={t("periodes.trenteJours")}
          valeur={tableau.comptes.connectes30Jours}
          icone={Activity}
          href="/utilisateurs?recents=1"
        />
        <CarteChiffre
          libelle={t("chiffres.profils")}
          periode={aCeJour}
          valeur={t("chiffres.surTotal", { n: configures, total: tableau.profils.length })}
          precision={t("chiffres.profilsPrecision")}
          icone={ShieldCheck}
          href="#profils"
        />
        <CarteChiffre
          libelle={t("chiffres.disponibilite")}
          periode={t("periodes.maintenant")}
          valeur={t("chiffres.surTotal", { n: disponibles, total: tableau.services.length })}
          precision={t("chiffres.disponibilitePrecision")}
          icone={Activity}
          href="#services"
        />
      </GrilleChiffres>

      <div className="grid gap-6 lg:grid-cols-2">
        <section id="services" aria-labelledby="titre-services" className="scroll-mt-24">
          <Carte>
            <h2 id="titre-services" className="mb-3 text-xl">
              {t("services.titre")}
            </h2>
            <ul className="divide-y divide-bordure">
              {tableau.services.map((s) => (
                <li key={s.cle} className="flex min-h-11 items-center justify-between gap-3 py-2">
                  <span className="flex flex-col">
                    <span className="font-semibold text-navy">{t(`services.${s.cle}.nom`)}</span>
                    <span className="text-sm text-texte-doux">{t(`services.${s.cle}.role`)}</span>
                  </span>
                  <BadgeStatut ton={s.disponible ? "succes" : "danger"}>
                    {s.disponible ? t("services.disponible") : t("services.indisponible")}
                  </BadgeStatut>
                </li>
              ))}
            </ul>
            <p className="mt-3 text-sm text-texte-doux">
              {t("services.verifie", { date: formaterDateHeure(tableau.verifieLe) })}
            </p>
          </Carte>
        </section>

        <Carte titre={t("operations.titre")}>
          {operations.length === 0 ? (
            <p className="text-texte-doux">{t("operations.vide")}</p>
          ) : (
            <ul className="divide-y divide-bordure">
              {operations.map((o) => (
                <li key={o.id}>
                  <Link
                    href={`/journal-audit/${o.id}`}
                    className="flex min-h-11 flex-col py-2 hover:bg-fond"
                  >
                    <span className="font-semibold text-navy">
                      {t(`operations.nature.${o.operation}`, {
                        objet: objetOperation(o),
                      })}
                    </span>
                    <span className="text-sm text-texte-doux">
                      {formaterDateHeure(o.date)} · {o.auteur ?? t("operations.systeme")}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
          <Link
            href="/journal-audit"
            className="mt-3 inline-flex min-h-11 items-center gap-2 font-semibold text-royal underline-offset-4 hover:underline"
          >
            <ScrollText aria-hidden="true" className="size-4" />
            {t("operations.voirTout")}
          </Link>
        </Carte>
      </div>

      <section id="profils" aria-labelledby="titre-profils" className="scroll-mt-24">
        <h2 id="titre-profils" className="mb-3 text-xl">
          {t("profils.titre")}
        </h2>
        <TableauDonnees
          legende={t("profils.titre")}
          lignes={tableau.profils}
          cleLigne={(p) => p.code}
          Lien={LienNext}
          lienLigne={(p) => `/utilisateurs?profil=${p.code}`}
          colonnes={colonnes}
        />
      </section>
    </div>
  );
}
