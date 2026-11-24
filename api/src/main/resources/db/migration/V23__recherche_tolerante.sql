-- e-mine · V23 — Recherche tolérante (lot « Listes et recherches »). Cahier des charges : « Recherche
-- instantanée et filtres sur chaque tableau de chaque menu ». Forme de comparaison d'un texte :
-- minuscules, sans accents ni ligatures (« Société Minière » → « societe miniere »). Fonction pure,
-- sans accès aux tables : la RLS s'applique normalement aux lignes comparées. Les fautes de frappe
-- sont tolérées par pg_trgm (installée à l'amorçage, `word_similarity`).

CREATE FUNCTION fn_normaliser(p_texte text) RETURNS text
  LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
  SET search_path = pg_catalog
AS $$
  SELECT translate(
           replace(replace(lower(p_texte), 'œ', 'oe'), 'æ', 'ae'),
           'àâäáãåçéèêëíìîïñóòôöõúùûüýÿ’''',
           'aaaaaaceeeeiiiinooooouuuuyy  ')
$$;

COMMENT ON FUNCTION fn_normaliser(text) IS
  'Texte en minuscules, sans accents ni ligatures, apostrophes remplacées par des espaces (recherche tolérante)';

GRANT EXECUTE ON FUNCTION fn_normaliser(text) TO emine_app;
