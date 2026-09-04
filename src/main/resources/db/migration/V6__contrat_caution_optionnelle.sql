-- Caution facultative (cf. Contrat.montantCaution / dureeCautionMois) : masquée par défaut dans
-- le formulaire (bouton "+" à côté du loyer), de nombreux dossiers physiques repris n'en portent
-- aucune trace. Les deux colonnes sont toujours renseignées ou absentes ensemble (cf.
-- AdminController), jamais l'une sans l'autre.
ALTER TABLE public.contrats ALTER COLUMN montant_caution DROP NOT NULL;
ALTER TABLE public.contrats ALTER COLUMN duree_caution_mois DROP NOT NULL;
