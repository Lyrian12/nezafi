-- Prix facultatif (cf. Emplacement.prix) : certains dossiers physiques repris n'ont pas de prix
-- connu avec certitude, même raison que les dates de bail et la caution sur Contrat (V5, V6).
ALTER TABLE public.emplacements ALTER COLUMN prix DROP NOT NULL;
