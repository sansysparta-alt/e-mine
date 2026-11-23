import { PageIntrouvable } from "@/composants/page-introuvable";
import { NavigationApplication } from "@/navigation/navigation-application";
import { chargerMoi } from "@/serveur/donnees";

/**
 * Adresse sans écran, ou écran réservé à un autre profil (le proxy y renvoie, statut 404) : la même
 * page « introuvable », dans le gabarit de l'espace connecté (menu de l'utilisateur) quand une
 * session existe ; sinon sur fond clair. Rien ne distingue un écran réservé d'une adresse inconnue.
 */
export default async function IntrouvableRacine() {
  const moi = await chargerMoi().catch(() => null);
  if (moi) {
    return (
      <NavigationApplication
        utilisateur={{ nomComplet: `${moi.prenom} ${moi.nom}`, profil: moi.profil.libelle }}
        profil={{ permissions: moi.permissions, interne: moi.interne }}
      >
        <PageIntrouvable />
      </NavigationApplication>
    );
  }
  return (
    <main id="contenu" className="mx-auto flex min-h-dvh max-w-2xl items-center bg-fond px-4">
      <div className="w-full">
        <PageIntrouvable />
      </div>
    </main>
  );
}
