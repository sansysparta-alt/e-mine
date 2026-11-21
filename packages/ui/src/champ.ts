/**
 * Style commun des champs de formulaire (select, input) : hauteur tactile de 44 px, bordure
 * contrastée, état invalide signalé par la couleur et l'épaisseur (jamais par la couleur seule :
 * le message d'erreur est toujours affiché sous le champ).
 */
export const classesChamp =
  "min-h-11 w-full rounded-lg border-2 border-bordure bg-white px-3 py-2 text-base text-texte aria-[invalid=true]:border-danger";
