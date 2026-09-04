-- Montant dû facultatif (cf. Echeance.montantDu) : certains dossiers physiques repris n'ont pas
-- ce montant connu avec certitude à la saisie, même raison que le prix d'un emplacement (V7) et
-- la caution d'un contrat (V6).
ALTER TABLE public.echeances ALTER COLUMN montant_du DROP NOT NULL;
