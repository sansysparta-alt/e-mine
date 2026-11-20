-- e-mine · V4 — les vues sur demande et paiement appliquent la RLS de l'appelant (US-01.5)
-- Dans le schéma fourni, ces vues appartiennent à emine_owner, qui n'est pas soumis aux politiques :
-- interrogées par emine_app, elles montraient les demandes et paiements de tous les usagers.
-- security_invoker (PostgreSQL 15+) fait vérifier les droits et la RLS avec le rôle appelant.
-- Le rôle de reporting (emine_readonly) garde sa lecture complète grâce à ses politiques USING (true).
-- v_titre_public et v_titre_echeance ne lisent pas de table protégée par RLS : inchangées.

ALTER VIEW v_demande_suivi        SET (security_invoker = true);
ALTER VIEW v_paiement_a_verifier  SET (security_invoker = true);
ALTER VIEW v_encaissement_mensuel SET (security_invoker = true);
ALTER VIEW v_demande_par_statut   SET (security_invoker = true);
