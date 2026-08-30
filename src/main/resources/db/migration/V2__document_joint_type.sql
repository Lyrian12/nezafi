-- Un contrat peut désormais avoir plusieurs catégories de documents (facture, scan du contrat
-- signé, ...) attachées à la même entité ("Contrat") : sans ce champ, remplacer l'un écraserait
-- l'autre (la logique de remplacement unique se basait uniquement sur nomEntite+entiteId).
-- NULL pour les documents qui n'ont jamais eu besoin de cette distinction (photos d'emplacement,
-- reçu de paiement — un seul type possible par entité, pas de risque de collision).
ALTER TABLE public.documents_joints ADD COLUMN type_document character varying(255);

-- Backfill : toute facture de contrat déjà attachée avant ce changement a type_document=NULL
-- (seul type possible pour "Contrat" jusqu'ici) — sans ce rattrapage, ces documents deviendraient
-- invisibles pour la recherche typée "FACTURE" introduite par ce changement.
UPDATE public.documents_joints SET type_document = 'FACTURE' WHERE nom_entite = 'Contrat' AND type_document IS NULL;
