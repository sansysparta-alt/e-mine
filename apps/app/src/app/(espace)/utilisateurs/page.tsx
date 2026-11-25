import {
  lireTableauAdministration,
  listerUtilisateurs,
  type UtilisateurDto,
} from "@emine/api-client";
import {
  Alerte,
  BadgeStatut,
  Bouton,
  classesAuDessusDuLien,
  type ColonneTableau,
  EnTetePage,
  EtatVide,
  FiltresPastilles,
  formaterDateHeure,
  TableauDonnees,
  tailleAutorisee,
  type TonBadge,
  TexteSurligne,
} from "@emine/ui";
import { ExternalLink, Users } from "@emine/ui/icones";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { type FiltreActif, FiltresActifs } from "@/composants/liste/filtres-actifs";
import { PaginationListe } from "@/composants/liste/pagination-liste";
import { PlusDeFiltres } from "@/composants/liste/plus-de-filtres";
import { RechercheListe } from "@/composants/liste/recherche-liste";
import { libelleProfil } from "@/composants/referentiels";
import { appelPage, chargerMoi, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("administration.utilisateurs");
  return { title: t("titre") };
}

const STATUTS = ["actif", "suspendu", "invite"] as const;
type Statut = (typeof STATUTS)[number];
const TONS: Record<Statut, TonBadge> = { actif: "succes", suspendu: "danger", invite: "info" };

type Parametres = {
  profil?: string;
  statut?: string;
  recents?: string;
  q?: string;
  page?: string;
  taille?: string;
};

/**
 * Utilisateurs & rôles (cahier des charges, espace « Administrateur technique ») : comptes, profil
 * et ses droits, statut et dernière connexion, en lecture seule ; chaque compte s'ouvre dans
 * Keycloak, où il se gère (D8). Filtres par statut (avec leur nombre, qui suit le profil et la
 * recherche choisis), par profil (le bouton affiche le profil choisi), recherche tolérante.
 */
export default async function Utilisateurs({
  searchParams,
}: {
  searchParams: Promise<Parametres>;
}) {
  const brut = await searchParams;
  const moi = await chargerMoi();
  if (!moi.interne || !moi.permissions.includes("PARAMETRER")) notFound();
  const t = await getTranslations("administration.utilisateurs");
  const tReferentiels = await getTranslations("referentiels");
  const tAdmin = await getTranslations("administration");
  const tDroits = await getTranslations("administration.droits");

  const statut = STATUTS.find((s) => s === brut.statut);
  const recents = brut.recents === "1";
  const q = brut.q?.trim().slice(0, 100) || undefined;
  const page = Math.max(1, Number.parseInt(brut.page ?? "1", 10) || 1);
  const taille = tailleAutorisee(Number.parseInt(brut.taille ?? "", 10));
  const options = await optionsApi();
  const tableau = (await appelPage("/utilisateurs", async () => lireTableauAdministration(options)))
    .data;
  const profil = tableau.profils.find((p) => p.code === brut.profil)?.code;
  const resultat = (
    await appelPage("/utilisateurs", async () =>
      listerUtilisateurs(
        { profil, statut, recents: recents || undefined, q, page, taille },
        options,
      ),
    )
  ).data;

  const adresse = (changes: Partial<Record<keyof Parametres, string | undefined>>) => {
    const p = new URLSearchParams();
    const tout = { profil, statut, recents: recents ? "1" : undefined, q, ...changes };
    for (const [cle, valeur] of Object.entries(tout)) if (valeur) p.set(cle, valeur);
    const chaine = p.toString();
    return chaine ? `/utilisateurs?${chaine}` : "/utilisateurs";
  };
  const nomProfil = (code: string, repli: string) => libelleProfil(code, repli, tReferentiels);
  // Compteurs de l'API : ils suivent le profil et la recherche choisis.
  const c = resultat.compteurs;
  const total = c.tous;
  const nombres: Record<Statut, number> = {
    actif: c.actifs,
    suspendu: c.suspendus,
    invite: c.invites,
  };
  const profilChoisi = tableau.profils.find((p) => p.code === profil);
  const droitsDe = (code: string) =>
    tableau.profils.find((p) => p.code === code)?.permissions ?? [];
  const filtresActifs: FiltreActif[] = [
    recents ? { cle: "recents", libelle: t("filtres.recents") } : null,
  ]
    .filter((f) => f !== null)
    .map((f) => ({ ...f, effacer: adresse({ [f.cle]: undefined }) }));

  const colonnes: ColonneTableau<UtilisateurDto>[] = [
    {
      cle: "utilisateur",
      entete: t("colonnes.utilisateur"),
      rendu: (u) => (
        <span className="flex flex-col">
          <span className="font-semibold text-navy">
            <TexteSurligne texte={`${u.prenom} ${u.nom}`} recherche={q ?? ""} />
          </span>
          <span className="text-sm whitespace-nowrap text-texte-doux">
            <TexteSurligne texte={u.email} recherche={q ?? ""} />
          </span>
        </span>
      ),
    },
    {
      cle: "statut",
      entete: t("colonnes.statut"),
      rendu: (u) => {
        const s = STATUTS.find((x) => x === u.statut) ?? "actif";
        return <BadgeStatut ton={TONS[s]}>{t(`statuts.${s}`)}</BadgeStatut>;
      },
    },
    {
      cle: "profil",
      entete: t("colonnes.profil"),
      interactive: true,
      rendu: (u) => {
        const droits = droitsDe(u.profilCode);
        return (
          <span className="flex flex-col">
            <span>{nomProfil(u.profilCode, u.profilLibelle)}</span>
            {u.organisation ? (
              <span className="text-sm text-texte-doux">
                <TexteSurligne texte={u.organisation} recherche={q ?? ""} />
              </span>
            ) : null}
            {droits.length > 0 ? (
              <details className={classesAuDessusDuLien}>
                <summary className="inline-flex min-h-11 cursor-pointer items-center text-sm font-semibold text-royal">
                  {tAdmin("profils.voirDroits", { nombre: droits.length })}
                </summary>
                <ul className="flex flex-col gap-1 pb-2 text-sm">
                  {droits.map((d) => (
                    <li key={d}>{tDroits.has(d) ? tDroits(d) : d}</li>
                  ))}
                </ul>
              </details>
            ) : (
              <span className="text-sm text-avertissement">{tAdmin("profils.sansDroit")}</span>
            )}
          </span>
        );
      },
    },
    {
      cle: "connexion",
      entete: t("colonnes.connexion"),
      rendu: (u) =>
        u.derniereConnexion ? (
          formaterDateHeure(u.derniereConnexion)
        ) : (
          <span className="text-texte-doux">{t("jamais")}</span>
        ),
    },
    {
      cle: "actions",
      entete: t("colonnes.actions"),
      interactive: true,
      rendu: (u) =>
        u.lienKeycloak ? (
          <a
            href={u.lienKeycloak}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex min-h-11 items-center gap-1 rounded-lg px-2 text-sm font-semibold whitespace-nowrap text-royal hover:bg-ice"
          >
            <ExternalLink aria-hidden="true" className="size-4" />
            {t("ouvrirKeycloak")}
            <span className="sr-only">
              {" "}
              {u.prenom} {u.nom} {t("nouvelOnglet")}
            </span>
          </a>
        ) : (
          <span className="text-texte-doux">—</span>
        ),
    },
  ];

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage titre={t("titre")} description={t("description")} />
      <Alerte nature="info" titre={t("keycloak.titre")}>
        {t("keycloak.texte")}
      </Alerte>
      <RechercheListe
        libelle={t("recherche.libelle")}
        valeur={q}
        placeholder={t("recherche.exemple")}
        boutonLibelle={t("recherche.bouton")}
        conserver={{ profil, statut, recents: recents ? "1" : undefined, taille: String(taille) }}
      />
      <FiltresPastilles
        libelle={t("filtres.libelle")}
        options={[
          {
            valeur: "",
            libelle: t("filtres.tous"),
            href: adresse({ statut: undefined }),
            actif: !statut,
            nombre: total,
          },
          ...STATUTS.map((s) => ({
            valeur: s,
            libelle: t(`filtres.${s}`),
            href: adresse({ statut: s }),
            actif: statut === s,
            nombre: nombres[s],
          })),
        ]}
        fin={
          <PlusDeFiltres
            libelle={t("filtres.plus")}
            titre={t("filtres.parProfil")}
            options={tableau.profils.map((p) => ({
              valeur: p.code,
              libelle: nomProfil(p.code, p.libelle),
              href: adresse({ profil: p.code }),
              actif: profil === p.code,
              nombre: p.utilisateurs,
            }))}
            actif={
              profilChoisi
                ? {
                    libelle: nomProfil(profilChoisi.code, profilChoisi.libelle),
                    effacer: adresse({ profil: undefined }),
                    libelleEffacer: t("filtres.effacer"),
                  }
                : undefined
            }
          />
        }
      />
      <FiltresActifs
        titre={t("filtres.actifs")}
        libelleEffacer={t("filtres.effacer")}
        filtres={filtresActifs}
      />
      {resultat.total === 0 ? (
        <EtatVide
          icone={Users}
          titre={q ? t("vide.recherche", { texte: q }) : t("vide.filtre")}
          description={q ? t("vide.conseil") : undefined}
          action={
            <Bouton asChild variante="secondaire">
              <Link href="/utilisateurs">{t("vide.effacer")}</Link>
            </Bouton>
          }
        />
      ) : (
        <div className="flex flex-col gap-4">
          <TableauDonnees
            legende={t("titre")}
            lignes={resultat.elements}
            cleLigne={(u) => u.id}
            colonnes={colonnes}
          />
          <PaginationListe
            chemin="/utilisateurs"
            parametres={{ profil, statut, recents: recents ? "1" : undefined, q }}
            page={resultat.page}
            taille={resultat.taille}
            total={resultat.total}
            elements={{ un: t("element"), plusieurs: t("elements") }}
          />
        </div>
      )}
    </div>
  );
}
