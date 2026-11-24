import { Fragment } from "react";

/**
 * Forme de comparaison d'un texte : minuscules, sans accents ni ponctuation superflue
 * (« Société Minière » → « societe miniere »). Sert aux recherches tolérantes du socle.
 */
export function normaliser(texte: string): string {
  return texte.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase();
}

/** Mots de la recherche, normalisés (les mots vides sont ignorés). */
function mots(requete: string): string[] {
  return normaliser(requete).split(/\s+/).filter(Boolean);
}

/**
 * Le texte correspond-il à la recherche ? Chaque mot saisi doit s'y retrouver, sans tenir compte
 * des accents ni des majuscules, dans n'importe quel ordre (« demande nouv » → « Nouvelle demande »).
 */
export function correspond(texte: string, requete: string): boolean {
  const cible = normaliser(texte);
  return mots(requete).every((m) => cible.includes(m));
}

/**
 * Découpe le texte en morceaux surlignés ou non, selon les mots de la recherche (accents et
 * majuscules ignorés, texte d'origine conservé à l'affichage).
 */
export function decouper(texte: string, requete: string): { texte: string; surligne: boolean }[] {
  const recherches = mots(requete);
  if (recherches.length === 0) return [{ texte, surligne: false }];
  // Forme normalisée caractère par caractère, avec la position d'origine de chacun.
  const origine: number[] = [];
  let cible = "";
  for (let i = 0; i < texte.length; i++) {
    const n = normaliser(texte[i] ?? "");
    for (let k = 0; k < n.length; k++) origine.push(i);
    cible += n;
  }
  const marque = new Array<boolean>(texte.length).fill(false);
  for (const m of recherches) {
    let depart = cible.indexOf(m);
    while (depart !== -1) {
      for (let j = depart; j < depart + m.length; j++) marque[origine[j] ?? 0] = true;
      depart = cible.indexOf(m, depart + m.length);
    }
  }
  const morceaux: { texte: string; surligne: boolean }[] = [];
  for (let i = 0; i < texte.length; i++) {
    const dernier = morceaux[morceaux.length - 1];
    if (dernier && dernier.surligne === marque[i]) dernier.texte += texte[i];
    else morceaux.push({ texte: texte[i] ?? "", surligne: marque[i] ?? false });
  }
  return morceaux;
}

/**
 * Texte dont les parties trouvées par la recherche sont surlignées (`<mark>`, contraste AA, et
 * graisse en plus de la couleur pour ne pas reposer sur la couleur seule).
 */
export function TexteSurligne({ texte, recherche }: { texte: string; recherche: string }) {
  return (
    <>
      {decouper(texte, recherche).map((m, i) =>
        m.surligne ? (
          <mark key={i} className="rounded-sm bg-or/30 font-semibold text-inherit">
            {m.texte}
          </mark>
        ) : (
          <Fragment key={i}>{m.texte}</Fragment>
        ),
      )}
    </>
  );
}
