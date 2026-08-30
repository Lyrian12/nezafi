-- Nouveau statut EXPIRE (cf. StatutContrat) : un contrat VALIDER dont la dateFin est dépassée
-- passe automatiquement à EXPIRE (recalcul paresseux à l'affichage, cf. ContratStatusService),
-- au lieu de rester affiché comme VALIDER indéfiniment. Le CHECK constraint généré par
-- Hibernate lors du schéma initial (V1) doit être élargi pour accepter cette nouvelle valeur.
ALTER TABLE public.contrats DROP CONSTRAINT contrats_statut_check;
ALTER TABLE public.contrats ADD CONSTRAINT contrats_statut_check
    CHECK (((statut)::text = ANY ((ARRAY['VALIDER'::character varying, 'EN_ATTENTE'::character varying, 'REJETER'::character varying, 'RESILIER'::character varying, 'EXPIRE'::character varying])::text[])));
