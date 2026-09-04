-- Dates de bail facultatives (cf. Contrat.dateDebut / dateFin) : certains dossiers physiques
-- repris n'ont pas ces dates connues avec certitude. La logique métier (ContratStatusService,
-- AdminAlertService, exports...) gérait déjà l'absence de dateFin par précaution ; seule la
-- contrainte NOT NULL du schéma initial (V1) l'empêchait encore en pratique.
ALTER TABLE public.contrats ALTER COLUMN date_debut DROP NOT NULL;
ALTER TABLE public.contrats ALTER COLUMN date_fin DROP NOT NULL;
